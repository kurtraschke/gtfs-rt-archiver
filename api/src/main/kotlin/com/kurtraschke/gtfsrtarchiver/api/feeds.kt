package com.kurtraschke.gtfsrtarchiver.api

import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents_
import io.javalin.http.Context
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlin.time.Duration

fun getFeeds(ctx: Context) {
    val em = ctx.entityManager

    val producer = ctx.pathParam("producer")

    val begin = ctx.attribute<Instant>("begin")!!
    val end = ctx.attribute<Instant>("end")!!

    val builder = em.criteriaBuilder
    val criteria = builder.createQuery(String::class.java)
    val root = criteria.from(FeedContents::class.java)

    criteria.select(root.get(FeedContents_.feed))
        .where(
            builder.equal(root.get(FeedContents_.isError), false),
            builder.equal(root.get(FeedContents_.producer), producer),
            builder.between(root.get(FeedContents_.fetchTime), begin.toJavaInstant(), end.toJavaInstant())
        )
        .distinct(true)

    val q = em.createQuery(criteria)

    ctx.json(q.resultList)
}