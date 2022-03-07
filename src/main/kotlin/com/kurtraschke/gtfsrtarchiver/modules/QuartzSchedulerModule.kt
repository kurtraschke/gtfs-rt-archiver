package com.kurtraschke.gtfsrtarchiver.modules

import com.kurtraschke.gtfsrtarchiver.GuiceJobFactory
import dev.misfitlabs.kotlinguice4.KotlinModule
import org.quartz.Scheduler
import org.quartz.impl.StdSchedulerFactory
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

class QuartzSchedulerModule : KotlinModule() {
    override fun configure() {
        bind<Scheduler>().toProvider<QuartzSchedulerProvider>().`in`(Singleton::class.java)
    }
}

class QuartzSchedulerProvider : Provider<Scheduler> {
    @Inject
    lateinit var jobFactory: GuiceJobFactory

    override fun get(): Scheduler {
        val scheduler = StdSchedulerFactory.getDefaultScheduler()
        scheduler.setJobFactory(jobFactory)

        return scheduler;
    }
}
