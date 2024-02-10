package com.kurtraschke.gtfsrtarchiver.api

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.protobuf.ExtensionRegistry
import com.hubspot.jackson.datatype.protobuf.ProtobufJacksonConfig
import com.hubspot.jackson.datatype.protobuf.ProtobufModule
import com.kurtraschke.gtfsrtarchiver.core.GtfsRealtimeExtensions
import io.javalin.http.Context
import io.javalin.http.pathParamAsClass
import io.javalin.validation.Validation.Companion.ValidationKey
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

fun parseDuration(ctx: Context) {
    val duration = ctx.pathParamAsClass<Duration>("duration")
        .check({ it <= MAX_DURATION }, "duration must not be greater than $MAX_DURATION")
        .get()

    val end = Clock.System.now()
    ctx.attribute("begin", end - duration)
    ctx.attribute("end", end)
}

fun parseBeginEnd(ctx: Context) {
    val begin = ctx.pathParamAsClass<Instant>("begin").get()
    val end = ctx.pathParamAsClass<Instant>("end")
        .check({ it > begin }, "'end' must be after 'begin'")
        .check({ (it - begin) <= MAX_DURATION }, "duration must not be greater than $MAX_DURATION")
        .get()

    ctx.attribute("begin", begin)
    ctx.attribute("end", end)
}

fun parseExtensions(ctx: Context) {
    val extensions = ctx.queryParams("extension").map {
        ctx.appData(ValidationKey).validator("extension", GtfsRealtimeExtensions::class.java, it)
    }

    ctx.attribute("extensions", extensions)
}

fun buildMapper(ctx: Context) {
    val extensions = ctx.attribute<List<GtfsRealtimeExtensions>>("extensions")!!

    val registry = ExtensionRegistry.newInstance()
    extensions.forEach { it.registerExtension(registry) }
    val config = ProtobufJacksonConfig.builder().extensionRegistry(registry).build()
    val mapper = ObjectMapper()
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.registerModule(ProtobufModule(config))

    ctx.attribute("mapper", mapper)
}