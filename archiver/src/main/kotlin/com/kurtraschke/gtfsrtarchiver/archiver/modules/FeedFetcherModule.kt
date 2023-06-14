package com.kurtraschke.gtfsrtarchiver.archiver.modules

import com.google.inject.AbstractModule
import com.kurtraschke.gtfsrtarchiver.archiver.DefaultFeedFetcher
import com.kurtraschke.gtfsrtarchiver.archiver.FeedFetcher
import jakarta.inject.Singleton

class FeedFetcherModule : AbstractModule() {
    override fun configure() {
        bind(FeedFetcher::class.java).to(DefaultFeedFetcher::class.java).`in`(Singleton::class.java)
    }
}
