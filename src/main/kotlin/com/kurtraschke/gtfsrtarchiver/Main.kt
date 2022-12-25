package com.kurtraschke.gtfsrtarchiver

import com.google.inject.ConfigurationException
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.persist.PersistService
import com.google.inject.persist.jpa.JpaPersistModule
import com.kurtraschke.gtfsrtarchiver.jobs.FeedArchiveJob
import com.kurtraschke.gtfsrtarchiver.listeners.JobFailureListener
import com.kurtraschke.gtfsrtarchiver.listeners.schedulerShutdownLatch
import com.kurtraschke.gtfsrtarchiver.listeners.schedulerStarted
import com.kurtraschke.gtfsrtarchiver.modules.FeedFetcherModule
import com.kurtraschke.gtfsrtarchiver.modules.OkHttpClientModule
import com.kurtraschke.gtfsrtarchiver.modules.ProjectVersionModule
import com.kurtraschke.gtfsrtarchiver.modules.QuartzSchedulerModule
import dev.misfitlabs.kotlinguice4.getInstance
import org.hibernate.cfg.Environment
import org.quartz.JobBuilder.newJob
import org.quartz.JobDataMap
import org.quartz.Scheduler
import org.quartz.SimpleScheduleBuilder.simpleSchedule
import org.quartz.TriggerBuilder.newTrigger
import org.quartz.impl.matchers.KeyMatcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import picocli.CommandLine
import picocli.CommandLine.*
import java.nio.file.Path
import javax.inject.Inject
import kotlin.system.exitProcess


@Command(
    name = "gtfs-rt-archiver", description = ["Archive GTFS-rt feeds to a PostgreSQL database"]
)
class Archiver : Runnable {
    private var log: Logger = LoggerFactory.getLogger(Archiver::class.java)

    @Inject
    lateinit var persistService: PersistService

    @Inject
    lateinit var scheduler: Scheduler

    @Inject
    lateinit var injector: Injector

    @Option(names = ["--one-shot"], description = ["Enable one-shot mode: archive each feed once, then exit"])
    var oneShot: Boolean = false

    @Parameters(index = "0", description = ["Path to configuration file"], paramLabel = "PATH")
    lateinit var configurationFile: Path

    override fun run() {
        try {
            val configuration = parseConfiguration(configurationFile)

            System.setProperty(Environment.URL, configuration.databaseUrl)

            persistService.start()

            configuration.feeds.groupingBy { Pair(it.producer, it.feed) }.eachCount().filterValues { it > 1 }
                .forEach {
                    log.warn(
                        "Feed {} {} is defined more than once; behavior is undefined", it.key.first, it.key.second
                    )
                }

            if (!oneShot) {
                configuration.feeds.forEach { feed ->
                    val fetchInterval = (feed.fetchInterval ?: configuration.fetchInterval ?: 30).coerceAtLeast(15)
                    val storeResponseBody = configuration.storeResponseBody ?: false
                    val storeResponseBodyOnError = configuration.storeResponseBodyOnError ?: true

                    val jobDataMap = JobDataMap()
                    jobDataMap["feed"] = feed
                    jobDataMap["storeResponseBody"] = storeResponseBody
                    jobDataMap["storeResponseBodyOnError"] = storeResponseBodyOnError

                    val job = newJob(FeedArchiveJob::class.java).withIdentity(feed.feed, feed.producer)
                        .usingJobData(jobDataMap).build()

                    scheduler.listenerManager.addJobListener(JobFailureListener(job.key), KeyMatcher.keyEquals(job.key))

                    val trigger = newTrigger().withIdentity(feed.feed, feed.producer).startNow().withSchedule(
                        simpleSchedule().withIntervalInSeconds(fetchInterval).repeatForever()
                    ).build()

                    scheduler.scheduleJob(job, trigger)
                }

                scheduler.start()
            } else {
                val feedFetcher = injector.getInstance<FeedFetcher>()

                configuration.feeds.forEach { feed ->
                    val storeResponseBody = configuration.storeResponseBody ?: false
                    val storeResponseBodyOnError = configuration.storeResponseBodyOnError ?: true

                    try {
                        MDC.put("producer", feed.producer)
                        MDC.put("feed", feed.feed)
                        val fc = feedFetcher.fetchFeed(feed, storeResponseBody, storeResponseBodyOnError)
                        fc?.let { log.info(it.toString()) }
                    } catch (e: Exception) {
                        log.error("Uncaught exception during feed fetch", e)
                    } finally {
                        MDC.clear()
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Shutting down scheduler.")
            scheduler.shutdown()
            throw e
        }
    }
}

fun main(args: Array<String>) {
    val exitCode = CommandLine(Archiver::class.java, GuiceFactory()).execute(*args)
    if (schedulerStarted) {
        schedulerShutdownLatch.await()
    }
    exitProcess(exitCode)
}

class GuiceFactory : IFactory {
    private val injector = Guice.createInjector(
        ProjectVersionModule(),
        OkHttpClientModule(),
        QuartzSchedulerModule(),
        JpaPersistModule("archiverUnit"),
        FeedFetcherModule()
    )

    override fun <K> create(aClass: Class<K>): K {
        return try {
            injector.getInstance(aClass)
        } catch (ex: ConfigurationException) {
            defaultFactory().create(aClass)
        }
    }
}
