package com.kurtraschke.gtfsrtarchiver.archiver.jobs

import com.google.inject.Inject
import com.kurtraschke.gtfsrtarchiver.archiver.Configuration
import com.kurtraschke.gtfsrtarchiver.archiver.FeedFetcher
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC

@DisallowConcurrentExecution
class FeedArchiveJob : Job {
    private var log: Logger = LoggerFactory.getLogger(FeedArchiveJob::class.java)

    @Inject
    lateinit var feedFetcher: FeedFetcher

    lateinit var feed: Configuration.Feed

    var storeResponseBody: Boolean = false
    var storeResponseBodyOnError: Boolean = true

    override fun execute(context: JobExecutionContext) {
        try {
            MDC.put("producer", feed.producer)
            MDC.put("feed", feed.feed)
            val fc = feedFetcher.fetchFeed(feed, storeResponseBody, storeResponseBodyOnError)
            context.result = fc
        } catch (e: Exception) {
            log.error("Uncaught exception during feed fetch", e)
            throw JobExecutionException(e)
        } finally {
            MDC.clear()
        }
    }
}
