package com.kurtraschke.gtfsrtarchiver.core.entities

import com.fasterxml.jackson.databind.JsonNode
import com.google.common.collect.ListMultimap
import io.hypersistence.utils.hibernate.type.json.JsonType
import okhttp3.Protocol
import org.hibernate.annotations.Type
import java.time.Instant
import jakarta.persistence.*

@Entity
@Table(name = "feed_contents")
@IdClass(FeedContentsKey::class)
data class FeedContents(
    @Id @Column(columnDefinition = "text") var producer: String,

    @Id @Column(columnDefinition = "text") var feed: String,

    @Id @Column(columnDefinition = "timestamptz") var fetchTime: Instant,

    var isError: Boolean,

    @Column(columnDefinition = "text") var errorMessage: String?,

    var statusCode: Int?,

    @Column(columnDefinition = "text") var statusMessage: String?,

    @Enumerated(EnumType.STRING) @Column(columnDefinition = "text") var protocol: Protocol?,

    @Type(JsonType::class) @Column(columnDefinition = "jsonb") var responseHeaders: ListMultimap<String, String>?,

    var responseTimeMillis: Int?
) {
    var responseBody: ByteArray? = null

    var responseBodyLength: Int? = 0

    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb")
    var responseContents: JsonNode? = null

    @Suppress("unused")
    var id: FeedContentsKey
        get() = FeedContentsKey(
            producer, feed, fetchTime
        )
        set(id) {
            producer = id.producer!!
            feed = id.feed!!
            fetchTime = id.fetchTime!!
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FeedContents

        if (producer != other.producer) return false
        if (feed != other.feed) return false
        if (fetchTime != other.fetchTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = producer.hashCode()
        result = 31 * result + feed.hashCode()
        result = 31 * result + fetchTime.hashCode()
        return result
    }
}

data class FeedContentsKey(
    var producer: String?,

    var feed: String?,

    var fetchTime: Instant?
) : java.io.Serializable {
    @Suppress("unused")
    constructor() : this(null, null, null)
}
