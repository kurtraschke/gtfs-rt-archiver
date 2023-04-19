package com.kurtraschke.gtfsrtarchiver.archiver.listeners

import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.archiver.jobs.JobUnpauserJob
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.quartz.*
import org.quartz.DateBuilder.IntervalUnit.SECOND
import org.quartz.DateBuilder.futureDate
import org.quartz.listeners.JobListenerSupport
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

private const val MAX_CONSECUTIVE_FAILURES = 3
private val PAUSE_PERIOD = 30.seconds
private const val PAUSE_ESCALATION = 2.0
private val MAX_PAUSE_DURATION = 1.hours
private val RESET_PAUSE_AFTER = 6.hours

class JobFailureListener(private val key: JobKey) : JobListenerSupport() {
    private var consecutiveFailureCount = 0
    private var pauseCount = 0
    private var lastFailure: Instant? = null

    override fun getName(): String = "JobFailureListener for ${this.key}"

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        val threwException = jobException != null
        val reportedError = (context.result as FeedContents?)?.isError == true

        val scheduler = context.scheduler
        val jobKey = context.jobDetail.key

        if (threwException || reportedError) {
            consecutiveFailureCount++
            lastFailure = Clock.System.now()

            if (consecutiveFailureCount >= MAX_CONSECUTIVE_FAILURES) {
                consecutiveFailureCount = 0
                pauseCount++

                var pauseDuration = (PAUSE_PERIOD * PAUSE_ESCALATION.pow(pauseCount))

                pauseDuration = jitter(pauseDuration, 0.1)
                pauseDuration = roundDuration(pauseDuration, 15.seconds)
                pauseDuration = pauseDuration.coerceAtMost(MAX_PAUSE_DURATION)

                log.warn(
                    "Pausing execution of job {} for {} due to consecutive failure count exceeding {}",
                    jobKey,
                    pauseDuration,
                    MAX_CONSECUTIVE_FAILURES
                )

                scheduler.pauseJob(jobKey)

                val jobDataMap = JobDataMap()
                jobDataMap["jobKey"] = jobKey

                val unpauseJob = JobBuilder.newJob(JobUnpauserJob::class.java).setJobData(jobDataMap).build()

                val unpauseTrigger =
                    TriggerBuilder.newTrigger().startAt(futureDate(pauseDuration.inWholeSeconds.toInt(), SECOND))
                        .build()

                scheduler.scheduleJob(unpauseJob, unpauseTrigger)
            }
        } else {
            consecutiveFailureCount = 0

            lastFailure?.let {
                if ((it - Clock.System.now()).absoluteValue >= RESET_PAUSE_AFTER) {
                    log.info(
                        "Resetting pause count for job {} as the last failure was at least {} ago",
                        jobKey,
                        RESET_PAUSE_AFTER
                    )
                    pauseCount = 0
                }
            }
        }
    }
}

fun roundDuration(duration: Duration, interval: Duration): Duration {
    return interval * ceil(duration / interval)
}

fun jitter(duration: Duration, jitterFactor: Double): Duration {
    require(jitterFactor in 0.0..1.0)
    val basePiece = duration * (1.0 - jitterFactor)
    val jitterable = duration * jitterFactor
    val withJitter = jitterable * Random.nextDouble()
    return basePiece + withJitter
}
