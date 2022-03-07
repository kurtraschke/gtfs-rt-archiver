package com.kurtraschke.gtfsrtarchiver.modules

import com.kurtraschke.gtfsrtarchiver.DefaultFeedFetcher
import com.kurtraschke.gtfsrtarchiver.FeedFetcher
import dev.misfitlabs.kotlinguice4.KotlinModule
import javax.inject.Singleton

class FeedFetcherModule : KotlinModule() {
    override fun configure() {
        bind<FeedFetcher>().to<DefaultFeedFetcher>().`in`<Singleton>()
    }
}
