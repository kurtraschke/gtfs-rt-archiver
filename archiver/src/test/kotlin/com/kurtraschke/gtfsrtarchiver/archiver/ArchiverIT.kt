package com.kurtraschke.gtfsrtarchiver.archiver

import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import picocli.CommandLine
import java.io.File
import kotlin.test.assertEquals

class ArchiverIT {


    @Test
    fun testArchiver() {
        val tempConfigFile = File.createTempFile("testConfiguration", ".conf")

        tempConfigFile.deleteOnExit()

        val testConfiguration = """
            databaseUrl = "jdbc:tc:postgresql:15.3:///archiver"
            fetchInterval = 30
            
            [[feeds]]
            producer = "MBTA"
            feed = "TU"
            feedUrl = "http://cdn.mbta.com/realtime/TripUpdates.pb"
            ignoreSSLErrors = true
            
            [[feeds]]
            producer = "MBTA"
            feed = "VP"
            feedUrl = "http://cdn.mbta.com/realtime/VehiclePositions.pb"
            ignoreSSLErrors = true
            
            [[feeds]]
            producer = "MBTA"
            feed = "Alerts"
            feedUrl = "http://cdn.mbta.com/realtime/Alerts.pb"
            ignoreSSLErrors = true
        """.trimIndent()

        tempConfigFile.writeText(testConfiguration)

        val cmd = CommandLine(Archiver::class.java, GuiceFactory())

        val exitCode = cmd.execute("--one-shot", tempConfigFile.absolutePath)

        assertEquals(0, exitCode)
    }

    companion object {
        @Suppress("unused")
        @JvmField
        @RegisterExtension
        var wm: WireMockExtension = WireMockExtension.newInstance()
            .options(wireMockConfig().port(80).httpsPort(443))
            .proxyMode(true)
            .build()
    }
}