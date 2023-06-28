package com.kurtraschke.gtfsrtarchiver.archiver.modules

import com.kurtraschke.gtfsrtarchiver.archiver.GuiceJobFactory
import com.kurtraschke.gtfsrtarchiver.archiver.listeners.SchedulerShutdownListener
import dev.misfitlabs.kotlinguice4.KotlinModule
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory

class QuartzSchedulerModule : KotlinModule() {
    override fun configure() {
        bind<Scheduler>().toProvider<QuartzSchedulerProvider>().`in`<Singleton>()
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
