package com.kurtraschke.gtfsrtarchiver.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.transit.realtime.GtfsRealtime.FeedMessage
import com.kurtraschke.gtfsrtarchiver.api.FetchMode.*
import com.kurtraschke.gtfsrtarchiver.api.ResponseFormat.*
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents_
import io.javalin.http.ContentType
import io.javalin.http.Context
import io.javalin.http.pathParamAsClass
import io.javalin.http.queryParamAsClass
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

enum class FetchMode {
    EXACT,
    CLOSEST,
    LATEST
}

enum class ResponseFormat {
    JSON,
    JSON_FULL,
    PB,
    PBTEXT
}

fun getFeedEntry(ctx: Context) {
    val em = ctx.entityManager

    val producer = ctx.pathParam("producer")
    val feed = ctx.pathParam("feed")

    val fetchTime: Instant
            by lazy {
                ctx.pathParamAsClass<Instant>("fetchTime")
                    .get()
            }

    val mapper = ctx.attribute<ObjectMapper>("mapper")!!

    val builder = em.criteriaBuilder
    val criteria = builder.createQuery(FeedContents::class.java)
    val root = criteria.from(FeedContents::class.java)

    val fetchMode = ctx.queryParamAsClass<FetchMode>("fetch_mode")
        .getOrDefault(EXACT)

    val responseFormat = ctx.pathParamAsClass<ResponseFormat>("format")
        .get()

    val q = when (fetchMode) {
        EXACT -> {
            criteria.select(root)
                .where(
                    builder.equal(root.get(FeedContents_.isError), false),
                    builder.equal(root.get(FeedContents_.producer), producer),
                    builder.equal(root.get(FeedContents_.feed), feed),
                    builder.equal(root.get(FeedContents_.fetchTime), fetchTime.toJavaInstant())
                )

            val q = em.createQuery(criteria)
            q
        }

        CLOSEST -> {
            criteria.select(root)
                .where(
                    builder.equal(root.get(FeedContents_.isError), false),
                    builder.equal(root.get(FeedContents_.producer), producer),
                    builder.equal(root.get(FeedContents_.feed), feed),
                )

            criteria.orderBy(
                builder.asc(
                    builder.abs(
                        builder.diff(
                            builder.function(
                                "date_part",
                                Integer::class.java,
                                builder.literal("epoch"),
                                root.get(FeedContents_.fetchTime)
                            ),
                            builder.literal(fetchTime.epochSeconds)
                        )
                    )
                )
            )

            val q = em.createQuery(criteria)
            q.maxResults = 1
            q
        }

        LATEST -> {
            criteria.select(root)
                .where(
                    builder.equal(root.get(FeedContents_.isError), false),
                    builder.equal(root.get(FeedContents_.producer), producer),
                    builder.equal(root.get(FeedContents_.feed), feed),
                )

            criteria.orderBy(builder.desc(root.get(FeedContents_.fetchTime)))

            val q = em.createQuery(criteria)
            q.maxResults = 1
            q
        }
    }

    val feedContents = q.singleResult

    when (responseFormat) {
        JSON -> ctx.json(feedContents.responseContents!!)
        JSON_FULL -> ctx.json(feedContents)
        PB -> {
            ctx.contentType("application/x-protobuf")
            val filename = "$producer-$feed-${timestampForFilename(fetchTime)}.pb"
            ctx.header("Content-Disposition", "attachment; filename=\"$filename\"")
            ctx.result(
                mapper.treeToValue(feedContents.responseContents, FeedMessage::class.java).toByteArray()
            )
        }

        PBTEXT -> {
            ctx.contentType(ContentType.TEXT_PLAIN)
            ctx.result(
                mapper.treeToValue(feedContents.responseContents, FeedMessage::class.java).toString()
            )
        }
    }
}