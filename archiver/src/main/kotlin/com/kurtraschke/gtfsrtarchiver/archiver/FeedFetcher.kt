package com.kurtraschke.gtfsrtarchiver.archiver

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableListMultimap.flatteningToImmutableListMultimap
import com.google.common.collect.ListMultimap
import com.google.inject.persist.Transactional
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.InvalidProtocolBufferException
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.kurtraschke.gtfsrtarchiver.archiver.Configuration.Feed
import com.kurtraschke.gtfsrtarchiver.archiver.FetchResult.FetchState.*
import com.kurtraschke.gtfsrtarchiver.core.GtfsRealtimeExtensions
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedStats
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedStats_
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.persistence.EntityManager
import jakarta.persistence.NoResultException
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.time.Instant

interface FeedFetcher {
    fun fetchFeed(
        feed: Feed, storeResponseBody: Boolean, storeResponseBodyOnError: Boolean
    ): FeedContents?
}

data class FetchResult(
    val fetchState: FetchState,
    val responseTimeMillis: Int?,
    val errorMessage: String?,
    val statusCode: Int?,
    val statusMessage: String?,
    val protocol: Protocol?,
    val responseHeaders: ListMultimap<String, String>?,
    val responseBody: ByteArray?,
    val feedMessage: FeedMessage?,
    val feedContents: JsonNode?
) {
    enum class FetchState {
        ERROR, //any error: IO errors, HTTP response codes indicating an error, protobuf problems, etc.
        NOT_MODIFIED, //we performed a conditional GET and got a 304 back
        UNCHANGED, //successful fetch but the header timestamp had not changed since the last fetch
        VALID //all other cases
    }

    constructor(fetchState: FetchState) : this(
        fetchState, null, null, null, null, null,
        null, null, null, null
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FetchResult

        if (fetchState != other.fetchState) return false
        if (responseTimeMillis != other.responseTimeMillis) return false
        if (errorMessage != other.errorMessage) return false
        if (statusCode != other.statusCode) return false
        if (statusMessage != other.statusMessage) return false
        if (protocol != other.protocol) return false
        if (responseHeaders != other.responseHeaders) return false
        if (responseBody != null) {
            if (other.responseBody == null) return false
            if (!responseBody.contentEquals(other.responseBody)) return false
        } else if (other.responseBody != null) return false
        if (feedMessage != other.feedMessage) return false
        if (feedContents != other.feedContents) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fetchState.hashCode()
        result = 31 * result + (responseTimeMillis ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + (statusCode ?: 0)
        result = 31 * result + (statusMessage?.hashCode() ?: 0)
        result = 31 * result + (protocol?.hashCode() ?: 0)
        result = 31 * result + (responseHeaders?.hashCode() ?: 0)
        result = 31 * result + (responseBody?.contentHashCode() ?: 0)
        result = 31 * result + (feedMessage?.hashCode() ?: 0)
        result = 31 * result + (feedContents?.hashCode() ?: 0)
        return result
    }
}

open class DefaultFeedFetcher : FeedFetcher {
    private var log: Logger = LoggerFactory.getLogger(DefaultFeedFetcher::class.java)

    @Inject
    lateinit var client: OkHttpClient

    @Inject
    lateinit var pem: Provider<EntityManager>

    @Transactional
    override fun fetchFeed(
        feed: Feed, storeResponseBody: Boolean, storeResponseBodyOnError: Boolean
    ): FeedContents? {
        val em = pem.get()

        val feedStats = getFeedStats(em, feed)

        val fetchTime = Instant.now()
        log.debug("Beginning fetch at {}", fetchTime)

        val feedUrlBuilder = feed.feedUrl.newBuilder()
        feed.queryParameters.orEmpty().forEach(feedUrlBuilder::addQueryParameter)

        val headersBuilder = feed.headers.orEmpty().toHeaders().newBuilder()

        val conditionalGet = feedStats?.etag != null || feedStats?.lastModified != null

        feedStats?.also {

            it.etag?.also {
                headersBuilder["If-None-Match"] = it
            }

            it.lastModified?.also {
                if (it <= fetchTime) {
                    headersBuilder["If-Modified-Since"] = it
                }
            }

        }

        val request = Request.Builder()
            .url(feedUrlBuilder.build())
            .headers(headersBuilder.build())
            .build()

        val customizedClient = client.newBuilder().apply {
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

        val fr = try {
            customizedClient.newCall(request).execute().use { response ->
                if (conditionalGet && response.code == HTTP_NOT_MODIFIED) {
                    FetchResult(NOT_MODIFIED)
                } else {
                    val isError = !response.isSuccessful

                    val statusCode = response.code
                    val statusMessage = response.message
                    val protocol = response.protocol

                    val responseHeaders = response
                        .headers
                        .toMultimap()
                        .entries
                        .stream()
                        .collect(flatteningToImmutableListMultimap({ it.key }, { it.value.stream() }))

                    val responseTimeMillis = (response.receivedResponseAtMillis - response.sentRequestAtMillis).toInt()
                    val responseBodyBytes = response.body!!.bytes()

                    if (!isError) {
                        try {
                            val (feedMessage, responseContents) = parseResponse(
                                responseBodyBytes, feed.extensions.orEmpty()
                            )

                            if (feedStats != null &&
                                (feedStats.gtfsRtHeaderTimestamp <= fetchTime
                                        && Instant.ofEpochSecond(feedMessage.header.timestamp) <= feedStats.gtfsRtHeaderTimestamp)
                            ) {
                                FetchResult(UNCHANGED)
                            } else {
                                FetchResult(
                                    VALID, responseTimeMillis, null, statusCode, statusMessage, protocol,
                                    responseHeaders, responseBodyBytes, feedMessage, responseContents
                                )
                            }
                        } catch (e: InvalidProtocolBufferException) {
                            log.warn("Protobuf decode exception", e)
                            FetchResult(
                                ERROR, responseTimeMillis, e.toString(), statusCode, statusMessage, protocol,
                                responseHeaders, responseBodyBytes, null, null
                            )
                        }
                    } else {
                        FetchResult(
                            ERROR, responseTimeMillis, null, statusCode, statusMessage, protocol, responseHeaders,
                            responseBodyBytes, null, null
                        )
                    }
                }
            }
        } catch (ie: IOException) {
            log.warn("IOException during feed fetch", ie)
            FetchResult(
                ERROR, null, ie.toString(), null, null, null,
                null, null, null, null
            )
        }

        val fc = when (fr.fetchState) {
            VALID, ERROR -> {
                val fc = FeedContents(
                    feed.producer, feed.feed, fetchTime, fr.fetchState == ERROR, fr.errorMessage,
                    fr.statusCode, fr.statusMessage, fr.protocol, fr.responseHeaders, fr.responseTimeMillis
                )

                fc.responseBodyLength = fr.responseBody?.size

                fc.responseBody = if (storeResponseBody || (fr.fetchState == ERROR && storeResponseBodyOnError)) {
                    fr.responseBody
                } else {
                    null
                }

                fc.responseContents = fr.feedContents

                em.persist(fc)

                fc
            }

            NOT_MODIFIED, UNCHANGED -> {
                log.debug("Not storing feed; status: {}", fr.fetchState)
                null
            }
        }

        em.close()

        return fc
    }
}

fun parseResponse(responseBodyBytes: ByteArray, extensions: List<GtfsRealtimeExtensions>): Pair<FeedMessage, JsonNode> {
    val registry = ExtensionRegistry.newInstance()
    extensions.forEach { it.registerExtension(registry) }
    val fm = FeedMessage.parseFrom(responseBodyBytes, registry)

    val config = ProtobufJacksonConfig.builder().extensionRegistry(registry).build()
    val mapper = ObjectMapper()
    mapper.registerModule(ProtobufModule(config))
    return Pair(fm, mapper.valueToTree(fm))
}

fun getFeedStats(em: EntityManager, feed: Feed): FeedStats? {
    val builder = em.criteriaBuilder
    val criteria = builder.createQuery(FeedStats::class.java)
    val root = criteria.from(FeedStats::class.java)
    criteria.select(root)
    criteria.where(
        builder.equal(root.get(FeedStats_.producer), feed.producer),
        builder.equal(root.get(FeedStats_.feed), feed.feed)
    )

    return try {
        em.createQuery(criteria).singleResult
    } catch (e: NoResultException) {
        null
    }
}
