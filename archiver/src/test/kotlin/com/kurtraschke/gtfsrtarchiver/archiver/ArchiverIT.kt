package com.kurtraschke.gtfsrtarchiver.archiver

import org.junit.jupiter.api.Test
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
            feedUrl = "https://cdn.mbta.com/realtime/TripUpdates.pb"
            
            [[feeds]]
            producer = "MBTA"
            feed = "VP"
            feedUrl = "https://cdn.mbta.com/realtime/VehiclePositions.pb"
            
            [[feeds]]
            producer = "MBTA"
            feed = "Alerts"
            feedUrl = "https://cdn.mbta.com/realtime/Alerts.pb"
        """.trimIndent()

        tempConfigFile.writeText(testConfiguration)

        val cmd = CommandLine(Archiver::class.java, GuiceFactory())

        val exitCode = cmd.execute("--one-shot", tempConfigFile.absolutePath)

        assertEquals(0, exitCode)
    }
}