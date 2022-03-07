package com.kurtraschke.gtfsrtarchiver.entities

import org.hibernate.annotations.Immutable
import java.time.Instant
import javax.persistence.*

@Entity
@Table(name = "v_feed_stats")
@Immutable
@IdClass(FeedStatsKey::class)
data class FeedStats(
    @Id @Column(columnDefinition = "text") var producer: String,

    @Id @Column(columnDefinition = "text") var feed: String,

    var fetchTime: Instant,

    var headerDate: Instant, var lastModified: Instant?, var etag: String?, var gtfsRtHeaderTimestamp: Instant

) {
    var id: FeedStatsKey
        get() = FeedStatsKey(
            producer, feed
        )
        set(id) {
            producer = id.producer!!
            feed = id.feed!!
        }
}

data class FeedStatsKey(
    var producer: String?,

    var feed: String?,
) : java.io.Serializable {
    constructor() : this(null, null) {}
}
