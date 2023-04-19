package com.kurtraschke.gtfsrtarchiver.archiver.listeners

import org.quartz.listeners.SchedulerListenerSupport
import java.util.concurrent.CountDownLatch

val schedulerShutdownLatch = CountDownLatch(1)
@Volatile
var schedulerStarted = false

class SchedulerShutdownListener : SchedulerListenerSupport() {
    override fun schedulerStarted() {
        schedulerStarted = true
    }

    override fun schedulerShutdown() {
        schedulerShutdownLatch.countDown()
    }
}
