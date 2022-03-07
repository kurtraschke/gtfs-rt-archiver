package com.kurtraschke.gtfsrtarchiver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.Streams
import com.google.inject.Inject
import com.google.inject.persist.Transactional
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.InvalidProtocolBufferException
import com.google.transit.realtime.GtfsRealtime
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.kurtraschke.gtfsrtarchiver.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.entities.FeedStats
import com.kurtraschke.gtfsrtarchiver.entities.FeedStats_
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import javax.inject.Provider
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.persistence.EntityManager
import javax.persistence.NoResultException

interface FeedFetcher {
    fun fetchFeed(
        feed: Configuration.Feed, storeResponseBody: Boolean, storeResponseBodyOnError: Boolean
    ): FeedContents?
}

open class DefaultFeedFetcher : FeedFetcher {
    private var log: Logger = LoggerFactory.getLogger(DefaultFeedFetcher::class.java)

    @Inject
    lateinit var client: OkHttpClient

    @Inject
    lateinit var pem: Provider<EntityManager>

    @Transactional
    override fun fetchFeed(
        feed: Configuration.Feed, storeResponseBody: Boolean, storeResponseBodyOnError: Boolean
    ): FeedContents? {
        val em = pem.get()
        val fetchTime = Instant.now()
        log.debug("Beginning fetch at $fetchTime")

        val builder = em.criteriaBuilder

        val criteria = builder.createQuery(FeedStats::class.java)
        val root = criteria.from(FeedStats::class.java)
        criteria.select(root)
        criteria.where(
            builder.equal(root.get(FeedStats_.producer), feed.producer),
            builder.equal(root.get(FeedStats_.feed), feed.feed)
        )

        val feedStats = try {
            em.createQuery(criteria).singleResult
        } catch (e: NoResultException) {
            null
        }

        val feedUrlBuilder = feed.feedUrl.newBuilder()
        feed.queryParameters.orEmpty().forEach(feedUrlBuilder::addQueryParameter)

        val headersBuilder = feed.headers.orEmpty().toHeaders().newBuilder()

        val conditionalGet = feedStats?.etag != null || feedStats?.lastModified != null

        feedStats?.let {
            it.etag?.let { headersBuilder.set("If-None-Match", it) }
            it.lastModified?.let { headersBuilder.set("If-Modified-Since", it) }
        }

        val request = Request.Builder().url(feedUrlBuilder.build()).headers(headersBuilder.build()).build()

        val localClient = client.newBuilder().apply {
            if (feed.ignoreSSLErrors == true) {
                ignoreAllSSLErrors()
            }

            feed.basicAuthCredentials?.let {
                authenticator(object : Authenticator {
                    override fun authenticate(route: Route?, response: Response): Request? {
                        if (response.request.header("Authorization") != null) {
                            return null
                        }

                        val credential = Credentials.basic(it.username, it.password)
                        return response.request.newBuilder().header("Authorization", credential).build()
                    }
                })
            }
        }.build()

        var fc: FeedContents

        try {
            localClient.newCall(request).execute().use { response ->
                if (conditionalGet && response.code == 304) {
                    log.debug("Received 304 in response to conditional GET")
                    return null
                }

                val isError = !response.isSuccessful

                val statusCode = response.code
                val statusMessage = response.message
                val protocol = response.protocol

                //Lower-case HTTP header keys for consistency
                val responseHeaders = Streams.stream(response.headers).collect(
                    ImmutableListMultimap.toImmutableListMultimap(
                        { p -> p.first.lowercase() }, Pair<String, String>::second
                    )
                )

                val responseTimeMillis = response.receivedResponseAtMillis - response.sentRequestAtMillis

                val responseBodyBytes = response.body!!.bytes()

                fc = FeedContents(
                    feed.producer,
                    feed.feed,
                    fetchTime,
                    isError,
                    null,
                    statusCode,
                    statusMessage,
                    protocol,
                    responseHeaders,
                    responseTimeMillis.toInt()
                )

                fc.responseBodyLength = responseBodyBytes.size

                if (!isError) {
                    try {
                        fc.responseContents = parseResponse(responseBodyBytes, feed.extensions.orEmpty())

                        val headerTimestamp =
                            Instant.ofEpochSecond(fc.responseContents!!.get("header").get("timestamp").asLong())

                        feedStats?.let {
                            if (headerTimestamp <= it.gtfsRtHeaderTimestamp) {
                                log.debug("Not storing result as GTFS-rt header timestamp is not newer than last fetched feed")
                                return null
                            }
                        }
                    } catch (e: InvalidProtocolBufferException) {
                        log.warn("Protobuf decode exception", e)
                        fc.isError = true
                        fc.errorMessage = e.toString()
                    }
                }

                if (storeResponseBody or (fc.isError and storeResponseBodyOnError)) {
                    fc.responseBody = responseBodyBytes
                }
            }
        } catch (ie: IOException) {
            log.warn("IOException during feed fetch", ie)
            fc = FeedContents(
                feed.producer, feed.feed, fetchTime, true, ie.toString(), null, null, null, null, null
            )
        }

        em.persist(fc)
        return fc
    }
}

fun parseResponse(responseBodyBytes: ByteArray, extensions: List<GtfsRealtimeExtensions>): JsonNode {
    val registry = ExtensionRegistry.newInstance()
    extensions.forEach { it.registerExtension(registry) }

    val config = ProtobufJacksonConfig.builder().extensionRegistry(registry).build()

    val fm = GtfsRealtime.FeedMessage.parseFrom(responseBodyBytes, registry)

    val mapper = ObjectMapper()
    mapper.registerModule(ProtobufModule(config))
    return mapper.valueToTree(fm)
}

fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
    val naiveTrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
    }

    val insecureSocketFactory = SSLContext.getInstance("TLSv1.2").apply {
        val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
        init(null, trustAllCerts, SecureRandom())
    }.socketFactory

    sslSocketFactory(insecureSocketFactory, naiveTrustManager)
    hostnameVerifier { _, _ -> true }
    return this
}
