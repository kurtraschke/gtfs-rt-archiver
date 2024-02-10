package com.kurtraschke.gtfsrtarchiver.api

import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents
import com.kurtraschke.gtfsrtarchiver.core.entities.FeedContents_
import io.javalin.http.Context
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant

fun getProducers(ctx: Context) {
    val em = ctx.entityManager

    val begin = ctx.attribute<Instant>("begin")!!
    val end = ctx.attribute<Instant>("end")!!

    val builder = em.criteriaBuilder
    val criteria = builder.createQuery(String::class.java)
    val root = criteria.from(FeedContents::class.java)

    criteria.select(root.get(FeedContents_.producer))
        .where(
            builder.equal(root.get(FeedContents_.isError), false),
            builder.between(root.get(FeedContents_.fetchTime), begin.toJavaInstant(), end.toJavaInstant())
        )
        .distinct(true)

    val q = em.createQuery(criteria)

    ctx.json(q.resultList)
}