package com.kurtraschke.gtfsrtarchiver.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ExtensionRegistry
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.google.transit.realtime.GtfsRealtimeNYCT
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents_
import io.javalin.Javalin
import io.javalin.event.EventListener
import io.javalin.http.ContentType
import io.javalin.http.Context
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.hibernate.cfg.Environment
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.NoResultException
import javax.persistence.Persistence
import kotlin.system.exitProcess
import kotlin.time.Duration

fun main(args: Array<String>): Unit = exitProcess(CommandLine(GtfsRtArchiverApi()).execute(*args))

@Command(name = "gtfs-rt-archiver-api", mixinStandardHelpOptions = true, version = ["1.0"])
class GtfsRtArchiverApi : Callable<Int> {

    @Option(names = ["-H", "--host"], required = false, defaultValue = "\${env:HOST}")
    var host = "localhost"

    @Option(names = ["-p", "--port"], required = false, defaultValue = "\${env:PORT}")
    var port = 7070

    @Option(names = ["-d", "--database"], required = true, defaultValue = "\${env:DATABASE_URL}")
    lateinit var databaseUrl: String

    override fun call(): Int {
        System.setProperty(Environment.URL, databaseUrl)

        val app = Javalin.create()
        val emf = Persistence.createEntityManagerFactory("archiverApiUnit")

        val registry = ExtensionRegistry.newInstance()
        GtfsRealtimeNYCT.registerAllExtensions(registry)
        val config = ProtobufJacksonConfig.builder().extensionRegistry(registry).build()
        val mapper = ObjectMapper()
        mapper.registerModule(ProtobufModule(config))

        enableRequestBoundEntityManager(app, emf)

        app.exception(NoResultException::class.java) { e, ctx ->
            ctx.status(404)
        }.error(404) { ctx ->
            ctx.result("Not Found")
        }

        app.get("/{duration}/producers") { ctx ->
            val em = ctx.entityManager

            val d = Duration.parse(ctx.pathParam("duration"))

            val end = Clock.System.now()
            val start = end.minus(d)

            val builder = em.criteriaBuilder
            val criteria = builder.createQuery(String::class.java)
            val root = criteria.from(FeedContents::class.java)

            criteria.select(root.get(FeedContents_.producer))
                .where(
                    builder.equal(root.get(FeedContents_.isError), false),
                    builder.between(root.get(FeedContents_.fetchTime), start.toJavaInstant(), end.toJavaInstant())
                )
                .distinct(true)

            val q = em.createQuery(criteria)

            ctx.json(q.resultList)
        }

        app.get("/{duration}/producers/{producer}/feeds") { ctx ->
            val em = ctx.entityManager

            val producer = ctx.pathParam("producer")

            val d = Duration.parse(ctx.pathParam("duration"))

            val end = Clock.System.now()
            val start = end.minus(d)

            val builder = em.criteriaBuilder
            val criteria = builder.createQuery(String::class.java)
            val root = criteria.from(FeedContents::class.java)

            criteria.select(root.get(FeedContents_.feed))
                .where(
                    builder.equal(root.get(FeedContents_.isError), false),
                    builder.equal(root.get(FeedContents_.producer), producer),
                    builder.between(root.get(FeedContents_.fetchTime), start.toJavaInstant(), end.toJavaInstant())
                )
                .distinct(true)

            val q = em.createQuery(criteria)

            ctx.json(q.resultList)
        }

        app.get("/{duration}/producers/{producer}/feeds/{feed}/") { ctx ->
            val em = ctx.entityManager

            val producer = ctx.pathParam("producer")
            val feed = ctx.pathParam("feed")

            val d = Duration.parse(ctx.pathParam("duration"))

            val end = Clock.System.now()
            val start = end.minus(d)

            val builder = em.criteriaBuilder
            val criteria = builder.createQuery(Instant::class.java)
            val root = criteria.from(FeedContents::class.java)

            criteria.select(root.get(FeedContents_.fetchTime))
                .where(
                    builder.equal(root.get(FeedContents_.isError), false),
                    builder.equal(root.get(FeedContents_.producer), producer),
                    builder.equal(root.get(FeedContents_.feed), feed),
                    builder.between(root.get(FeedContents_.fetchTime), start.toJavaInstant(), end.toJavaInstant())
                )
                .orderBy(builder.asc(root.get(FeedContents_.fetchTime)))

            val q = em.createQuery(criteria)

            ctx.json(q.resultStream.map { it.toString() }.toList())
        }

        app.get("/producers/{producer}/feeds/{feed}/{fetchTime}/{format}") { ctx ->
            val em = ctx.entityManager

            val producer = ctx.pathParam("producer")
            val feed = ctx.pathParam("feed")

            val fetchTime = Instant.parse(ctx.pathParam("fetchTime"))

            val builder = em.criteriaBuilder
            val criteria = builder.createQuery(FeedContents::class.java)
            val root = criteria.from(FeedContents::class.java)

            criteria.select(root)
                .where(
                    builder.equal(root.get(FeedContents_.isError), false),
                    builder.equal(root.get(FeedContents_.producer), producer),
                    builder.equal(root.get(FeedContents_.feed), feed),
                    builder.equal(root.get(FeedContents_.fetchTime), fetchTime)
                )

            val q = em.createQuery(criteria)

            val feedContents = q.singleResult

            when (ctx.pathParam("format")) {
                "json" -> ctx.json(feedContents.responseContents!!)
                "json-full" -> ctx.json(feedContents)
                "pb" -> {
                    ctx.contentType("application/x-protobuf")
                    val filename = "$producer-$feed-${timestampForFilename(fetchTime)}.pb"
                    ctx.header("Content-Disposition", "attachment; filename=\"$filename\"")
                    ctx.result(mapper.treeToValue(feedContents.responseContents, FeedMessage::class.java).toByteArray())
                }
                "pbtext" -> {
                    ctx.contentType(ContentType.TEXT_PLAIN)
                    ctx.result(mapper.treeToValue(feedContents.responseContents, FeedMessage::class.java).toString())
                }
            }
        }

        app.get("/{duration}/producers/{producer}/feeds/{feed}/bundle") { ctx ->
            val em = ctx.entityManager

            val producer = ctx.pathParam("producer")
            val feed = ctx.pathParam("feed")

            val d = Duration.parse(ctx.pathParam("duration"))

            val end = Clock.System.now()
            val start = end.minus(d)

            val builder = em.criteriaBuilder
            val criteria = builder.createQuery(FeedContents::class.java)
            val root = criteria.from(FeedContents::class.java)
            criteria.select(root)
            criteria.where(
                builder.equal(root.get(FeedContents_.isError), false),
                builder.equal(root.get(FeedContents_.producer), producer),
                builder.equal(root.get(FeedContents_.feed), feed),
                builder.between(root.get(FeedContents_.fetchTime), start.toJavaInstant(), end.toJavaInstant())
            )
            criteria.orderBy(builder.asc(root.get(FeedContents_.fetchTime)))

            val q = em.createQuery(criteria)
            val os = ctx.outputStream()

            val filename = "$producer-$feed-${timestampForFilename(end.toJavaInstant())}.tgz"

            ctx.contentType("application/gzip")
            ctx.header("Content-Disposition", "attachment; filename=\"$filename\"")

            GzipCompressorOutputStream(os).use { gzo ->
                TarArchiveOutputStream(gzo).use { aos ->
                    q.resultStream.forEach { feedContents ->
                        val protobufBytes =
                            mapper.treeToValue(feedContents.responseContents, FeedMessage::class.java).toByteArray()

                        val entryFilename = "${timestampForFilename(feedContents.fetchTime)}.pb"

                        val entry = TarArchiveEntry(entryFilename)
                        entry.size = protobufBytes.size.toLong()
                        entry.creationTime = FileTime.from(feedContents.fetchTime)

                        aos.putArchiveEntry(entry)
                        aos.write(protobufBytes)

                        aos.closeArchiveEntry()

                        em.detach(feedContents)
                    }
                    aos.finish()
                }
            }
        }

        val shutdownLatch = CountDownLatch(1)

        Runtime.getRuntime().addShutdownHook(Thread { app.stop() })

        app.events { event: EventListener ->
            event.serverStopped { shutdownLatch.countDown() }
        }

        app.start(host, port)

        shutdownLatch.await()

        return 0
    }
}

fun enableRequestBoundEntityManager(app: Javalin, entityManagerFactory: EntityManagerFactory) {
    app.attribute("entityManagerFactory", entityManagerFactory)

    app.after {
        it.attribute<EntityManager?>("entityManager")?.close()
    }
}

val Context.entityManager: EntityManager
    get() {
        if (this.attribute<EntityManager>("entityManager") == null) {
            this.attribute(
                "entityManager",
                this.appAttribute<EntityManagerFactory>("entityManagerFactory").createEntityManager()
            )
        }
        return this.attribute("entityManager")!!
    }

fun timestampForFilename(ts: Instant): String = ts.truncatedTo(ChronoUnit.SECONDS)
    .toString()
    .replace("-", "")
    .replace(":", "")