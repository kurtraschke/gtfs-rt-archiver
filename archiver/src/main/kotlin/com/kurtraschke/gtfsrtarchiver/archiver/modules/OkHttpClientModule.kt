package com.kurtraschke.gtfsrtarchiver.archiver.modules

import com.google.inject.AbstractModule
import com.google.inject.Inject
import com.google.inject.Provider
import com.google.inject.name.Named
import jakarta.inject.Singleton
import okhttp3.OkHttpClient

class OkHttpClientModule : AbstractModule() {
    override fun configure() {
        bind(OkHttpClient::class.java).toProvider(OkHttpClientProvider::class.java).`in`(Singleton::class.java)
    }
}

class OkHttpClientProvider : Provider<OkHttpClient> {
    @Inject
    @Named("project.artifactId")
    lateinit var artifactId: String

    @Inject
    @Named("project.version")
    lateinit var projectVersion: String

    @Inject
    @Named("git.commit.id.describe")
    lateinit var commitId: String

    @Inject
    @Named("git.branch")
    lateinit var branch: String

    override fun get(): OkHttpClient {
        val userAgentString = "$artifactId/$projectVersion ($commitId; $branch)"

        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request()
                        .newBuilder()
                        .header("User-Agent", userAgentString)
                        .build()
                )
            }
            .build()
    }
}