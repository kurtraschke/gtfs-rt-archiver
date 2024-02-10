package com.kurtraschke.gtfsrtarchiver.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents_
import io.javalin.http.Context
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.hibernate.jpa.AvailableHints.HINT_FETCH_SIZE
import java.nio.file.attribute.FileTime

fun getFeedEntriesBundle(ctx: Context) {
    val em = ctx.entityManager

    val producer = ctx.pathParam("producer")
    val feed = ctx.pathParam("feed")

    val begin = ctx.attribute<Instant>("begin")!!
    val end = ctx.attribute<Instant>("end")!!

    val mapper = ctx.attribute<ObjectMapper>("mapper")!!

    val builder = em.criteriaBuilder
    val criteria = builder.createQuery(FeedContents::class.java)

    val root = criteria.from(FeedContents::class.java)
    criteria.select(root)
    criteria.where(
        builder.equal(root.get(FeedContents_.isError), false),
        builder.equal(root.get(FeedContents_.producer), producer),
        builder.equal(root.get(FeedContents_.feed), feed),
        builder.between(root.get(FeedContents_.fetchTime), begin.toJavaInstant(), end.toJavaInstant())
    )

    em.transaction.begin()

    val q = em.createQuery(criteria)
    q.setHint(HINT_FETCH_SIZE, 1000)

    val os = ctx.res().outputStream

    val filename = "$producer-$feed-${timestampForFilename(end)}.tar.zst"

    ctx.contentType("application/zstd")
    ctx.header("Content-Disposition", "attachment; filename=\"$filename\"")

    ZstdCompressorOutputStream(os).use { gzo ->
        TarArchiveOutputStream(gzo).use { aos ->
            q.resultStream.forEach { feedContents ->
                val fm = mapper.treeToValue(feedContents.responseContents, FeedMessage::class.java)

                val protobufBytes = fm.toByteArray()

                val entryFilename = "${timestampForFilename(feedContents.fetchTime.toKotlinInstant())}.pb"

                val entry = TarArchiveEntry(entryFilename)
                entry.size = protobufBytes.size.toLong()
                entry.creationTime = FileTime.from(feedContents.fetchTime)

                aos.putArchiveEntry(entry)
                aos.write(protobufBytes)

                aos.closeArchiveEntry()

                em.detach(feedContents)
            }
            aos.finish()
        }
    }

    em.transaction.rollback()
}
