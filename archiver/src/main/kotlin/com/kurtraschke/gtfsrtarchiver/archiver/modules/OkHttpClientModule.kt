package com.kurtraschke.gtfsrtarchiver.archiver.modules

import dev.misfitlabs.kotlinguice4.KotlinModule
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Provider
import jakarta.inject.Singleton
import okhttp3.OkHttpClient

class OkHttpClientModule : KotlinModule() {
    override fun configure() {
        bind<OkHttpClient>().toProvider<OkHttpClientProvider>().`in`<Singleton>()
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