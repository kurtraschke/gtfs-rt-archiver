package com.kurtraschke.gtfsrtarchiver.api

import com.google.common.collect.ImmutableList
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents_
import io.javalin.http.Context
import kotlinx.datetime.toJavaInstant
import java.time.Instant

fun getFeedEntries(ctx: Context) {
    val em = ctx.entityManager

    val producer = ctx.pathParam("producer")
    val feed = ctx.pathParam("feed")

    val begin = ctx.attribute<kotlinx.datetime.Instant>("begin")!!
    val end = ctx.attribute<kotlinx.datetime.Instant>("end")!!

    val builder = em.criteriaBuilder
    val criteria = builder.createQuery(Instant::class.java)
    val root = criteria.from(FeedContents::class.java)

    criteria.select(root.get(FeedContents_.fetchTime))
        .where(
            builder.equal(root.get(FeedContents_.isError), false),
            builder.equal(root.get(FeedContents_.producer), producer),
            builder.equal(root.get(FeedContents_.feed), feed),
            builder.between(root.get(FeedContents_.fetchTime), begin.toJavaInstant(), end.toJavaInstant())
        )
        .orderBy(builder.asc(root.get(FeedContents_.fetchTime)))

    val q = em.createQuery(criteria)

    ctx.json(q.resultStream.map { it.toString() }.collect(ImmutableList.toImmutableList()))
}