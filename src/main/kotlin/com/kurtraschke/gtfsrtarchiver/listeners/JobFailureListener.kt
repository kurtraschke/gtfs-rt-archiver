package com.kurtraschke.gtfsrtarchiver.listeners

import com.kurtraschke.gtfsrtarchiver.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.jobs.JobUnpauserJob
import org.quartz.*
import org.quartz.DateBuilder.IntervalUnit.SECOND
import org.quartz.DateBuilder.futureDate
import org.quartz.listeners.JobListenerSupport
import kotlin.math.pow

class JobFailureListener : JobListenerSupport() {
    private var consecutiveFailureCount = 0
    private var pauseCount = 0
    private val maxConsecutiveFailures = 3

    override fun getName(): String = "JobFailureListener"

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        val threwException = jobException != null
        val reportedError = (context.result as FeedContents?)?.isError == true

        if (threwException || reportedError) {
            consecutiveFailureCount++

            if (consecutiveFailureCount >= maxConsecutiveFailures) {
                consecutiveFailureCount = 0
                pauseCount++

                val pauseDuration: Int = (2.0.pow(pauseCount.toDouble()).toInt() * 30).coerceAtMost(900)

                val scheduler = context.scheduler
                val jobKey = context.jobDetail.key

                log.warn(
                    "Pausing execution of job {} for {} seconds due to consecutive failure count exceeding {}",
                    jobKey,
                    pauseDuration,
                    maxConsecutiveFailures
                )
                scheduler.pauseJob(jobKey)

                val jobDataMap = JobDataMap()
                jobDataMap["jobKey"] = jobKey

                val unpauseJob = JobBuilder.newJob(JobUnpauserJob::class.java).setJobData(jobDataMap).build()

                val unpauseTrigger = TriggerBuilder.newTrigger().startAt(futureDate(pauseDuration, SECOND)).build()

                scheduler.scheduleJob(unpauseJob, unpauseTrigger)
            }
        } else {
            consecutiveFailureCount = 0
        }
    }
}

