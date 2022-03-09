package com.kurtraschke.gtfsrtarchiver.listeners

import org.quartz.listeners.SchedulerListenerSupport
import java.util.concurrent.CountDownLatch

val schedulerShutdownLatch: CountDownLatch = CountDownLatch(1)

class SchedulerShutdownListener : SchedulerListenerSupport() {
    override fun schedulerShutdown() {
        schedulerShutdownLatch.countDown()
    }
}
