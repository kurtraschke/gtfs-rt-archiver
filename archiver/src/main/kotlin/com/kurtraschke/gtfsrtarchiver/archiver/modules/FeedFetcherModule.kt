package com.kurtraschke.gtfsrtarchiver.archiver.modules

import com.kurtraschke.gtfsrtarchiver.archiver.DefaultFeedFetcher
import com.kurtraschke.gtfsrtarchiver.archiver.FeedFetcher
import dev.misfitlabs.kotlinguice4.KotlinModule
import javax.inject.Singleton

class FeedFetcherModule : KotlinModule() {
    override fun configure() {
        bind<FeedFetcher>().to<DefaultFeedFetcher>().`in`<Singleton>()
    }
}
