package com.kurtraschke.gtfsrtarchiver.archiver.jobs

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class JobUnpauserJob : Job {
    private var log: Logger = LoggerFactory.getLogger(JobUnpauserJob::class.java)
    lateinit var jobKey: JobKey

    override fun execute(context: JobExecutionContext) {
        log.info("Unpausing execution of job {}", jobKey)
        context.scheduler.resumeJob(jobKey)
    }
}
