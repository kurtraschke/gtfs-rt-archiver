package com.kurtraschke.gtfsrtarchiver.archiver

import com.google.inject.Injector
import org.quartz.Job
import org.quartz.Scheduler
import org.quartz.simpl.PropertySettingJobFactory
import org.quartz.spi.TriggerFiredBundle
import javax.inject.Inject


class GuiceJobFactory : PropertySettingJobFactory() {
    @Inject
    lateinit var injector: Injector

    override fun newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job {
        val job = super.newJob(bundle, scheduler)
        injector.injectMembers(job)

        return job
    }
}
