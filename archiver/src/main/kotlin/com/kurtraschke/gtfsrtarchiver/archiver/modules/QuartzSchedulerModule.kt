package com.kurtraschke.gtfsrtarchiver.archiver.modules

import com.google.inject.AbstractModule
import com.kurtraschke.gtfsrtarchiver.archiver.GuiceJobFactory
import com.kurtraschke.gtfsrtarchiver.archiver.listeners.SchedulerShutdownListener
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory

class QuartzSchedulerModule : AbstractModule() {
    override fun configure() {
        bind(Scheduler::class.java).toProvider(QuartzSchedulerProvider::class.java).`in`(Singleton::class.java)
    }
}

class QuartzSchedulerProvider : Provider<Scheduler> {
    @Inject
    lateinit var jobFactory: GuiceJobFactory

    override fun get(): Scheduler {
        val scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.listenerManager.addSchedulerListener(SchedulerShutdownListener())
        scheduler.setJobFactory(jobFactory)

        return scheduler
    }
}
