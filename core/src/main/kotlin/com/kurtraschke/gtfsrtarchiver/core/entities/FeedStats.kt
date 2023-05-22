package com.kurtraschke.gtfsrtarchiver.core.entities

import org.hibernate.annotations.Immutable
import java.time.Instant
import jakarta.persistence.*

@Entity
@Table(name = "v_feed_stats")
@Immutable
@IdClass(FeedStatsKey::class)
data class FeedStats(
    @Id @Column(columnDefinition = "text") var producer: String,

    @Id @Column(columnDefinition = "text") var feed: String,

    var fetchTime: Instant,
    var headerDate: Instant,
    var lastModified: Instant?,
    var etag: String?,
    var gtfsRtHeaderTimestamp: Instant
) {
    @Suppress("unused")
    var id: FeedStatsKey
        get() = FeedStatsKey(
            producer, feed
        )
        set(id) {
            producer = id.producer!!
            feed = id.feed!!
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FeedStats

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

data class FeedStatsKey(
    var producer: String?,

    var feed: String?,
) : java.io.Serializable {
    @Suppress("unused")
    constructor() : this(null, null)
}


