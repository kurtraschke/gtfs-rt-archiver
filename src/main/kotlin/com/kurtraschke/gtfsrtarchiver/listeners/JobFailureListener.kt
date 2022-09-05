package com.kurtraschke.gtfsrtarchiver.listeners

import com.kurtraschke.gtfsrtarchiver.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.jobs.JobUnpauserJob
import org.quartz.*
import org.quartz.DateBuilder.IntervalUnit.SECOND
import org.quartz.DateBuilder.futureDate
import org.quartz.listeners.JobListenerSupport
import java.time.Duration
import java.time.Instant
import kotlin.math.log
import kotlin.math.pow
import kotlin.time.Duration.Companion.hours
import kotlin.time.toKotlinDuration

private const val MAX_CONSECUTIVE_FAILURES = 3
private const val PAUSE_PERIOD = 30
private const val PAUSE_ESCALATION = 1.25
private const val MAX_PAUSE_DURATION = 900
private val RESET_PAUSE_AFTER = 6.hours
private val MAX_PAUSE_COUNT = log(MAX_PAUSE_DURATION / PAUSE_PERIOD.toDouble(), PAUSE_ESCALATION)

class JobFailureListener : JobListenerSupport() {
    private var consecutiveFailureCount = 0
    private var pauseCount = 0
    private var lastFailure: Instant? = null

    override fun getName(): String = "JobFailureListener"

    override fun jobWasExecuted(context: JobExecutionContext, jobException: JobExecutionException?) {
        val threwException = jobException != null
        val reportedError = (context.result as FeedContents?)?.isError == true

        val scheduler = context.scheduler
        val jobKey = context.jobDetail.key

        if (threwException || reportedError) {
            consecutiveFailureCount++

            if (consecutiveFailureCount >= MAX_CONSECUTIVE_FAILURES) {
                consecutiveFailureCount = 0
                pauseCount++

                val pauseDuration: Int = (PAUSE_ESCALATION.pow(pauseCount.toDouble().coerceAtMost(MAX_PAUSE_COUNT)) * PAUSE_PERIOD).toInt()

                log.warn(
                    "Pausing execution of job {} for {} seconds due to consecutive failure count exceeding {}",
                    jobKey,
                    pauseDuration,
                    MAX_CONSECUTIVE_FAILURES
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

            if (lastFailure != null) {
                if (Duration.between(lastFailure, Instant.now()).abs().toKotlinDuration() >= RESET_PAUSE_AFTER) {
                    log.info("Resetting pause time for job {} as the last failure was at least {} ago", jobKey, RESET_PAUSE_AFTER)
                    pauseCount = 0
                }
            }
        }
    }
}

