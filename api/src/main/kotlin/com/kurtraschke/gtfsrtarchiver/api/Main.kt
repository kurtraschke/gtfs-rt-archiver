package com.kurtraschke.gtfsrtarchiver.api

import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.kurtraschke.gtfsrtarchiver.core.GtfsRealtimeExtensions
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.*
import io.javalin.config.Key
import io.javalin.http.Context
import io.javalin.json.JavalinJackson
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.NoResultException
import jakarta.persistence.Persistence
import kotlinx.datetime.Instant
import org.hibernate.cfg.Environment
import org.slf4j.bridge.SLF4JBridgeHandler
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess
import kotlin.time.Duration

val MAX_DURATION = Duration.parse("PT36H")
val EntityManagerFactoryKey = Key<EntityManagerFactory>("entityManagerFactory")

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
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()

        System.setProperty(Environment.JAKARTA_JDBC_URL, databaseUrl)

        val emf = Persistence.createEntityManagerFactory("archiverApiUnit")

        val shutdownLatch = CountDownLatch(1)

        val app = Javalin.create { config ->
            config.validation.register(Duration::class.java, Duration::parse)
            config.validation.register(Instant::class.java, Instant::parse)
            config.validation.register(FetchMode::class.java) { FetchMode.valueOf(it.uppercase()) }
            config.validation.register(ResponseFormat::class.java) { ResponseFormat.valueOf(it.uppercase()) }
            config.validation.register(GtfsRealtimeExtensions::class.java) { GtfsRealtimeExtensions.valueOf(it.uppercase()) }

            //it.plugins.enableRouteOverview("route-overview")
            config.jsonMapper(JavalinJackson().updateMapper { mapper ->
                mapper.registerModule(GuavaModule())
            })

            config.appData(EntityManagerFactoryKey, emf)

            config.router.apiBuilder {
                path("duration/{duration}") {
                    before(::parseDuration)

                    registerCommonRoutes()
                }

                path("from/{begin}/to/{end}") {
                    before(::parseBeginEnd)

                    registerCommonRoutes()
                }

                path("producers/{producer}/feeds/{feed}/entries/{fetchTime}/format/{format}") {
                    before(::parseExtensions)
                    before(::buildMapper)
                    get(::getFeedEntry)
                }
            }

            config.events.serverStopped { shutdownLatch.countDown() }

        }

        app.after {
            val em = it.attribute<EntityManager?>("entityManager")

            if (em != null) {
                if (em.transaction.isActive) {
                    em.transaction.rollback()
                }
                em.close()
            }
        }

        app.exception(NoResultException::class.java) { _, ctx ->
            ctx.status(404)
        }

        app.error(404) { ctx ->
            ctx.result("Not Found")
        }

        Runtime.getRuntime().addShutdownHook(Thread { app.stop() })

        app.start(host, port)

        shutdownLatch.await()

        return 0
    }
}

fun registerCommonRoutes() {
    path("producers") {
        get(::getProducers)
    }

    path("producers/{producer}/feeds") {
        get(::getFeeds)
    }

    path("producers/{producer}/feeds/{feed}/entries") {
        get(::getFeedEntries)
    }

    path("producers/{producer}/feeds/{feed}/bundle") {
        before(::parseExtensions)
        before(::buildMapper)
        get(::getFeedEntriesBundle)
    }
}

val Context.entityManager: EntityManager
    get() {
        if (this.attribute<EntityManager>("entityManager") == null) {
            this.attribute(
                "entityManager",
                this.appData(EntityManagerFactoryKey).createEntityManager()
            )
        }
        return this.attribute("entityManager")!!
    }

