package com.kurtraschke.gtfsrtarchiver.modules

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import okhttp3.OkHttpClient

class OkHttpClientModule : AbstractModule() {
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val client = OkHttpClient()
        return client;
    }
}
