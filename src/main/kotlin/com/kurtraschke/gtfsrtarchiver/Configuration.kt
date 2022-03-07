package com.kurtraschke.gtfsrtarchiver

import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.transcoding.TomlDecoder
import cc.ekblad.toml.transcoding.decode
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.nio.file.Path

data class Configuration(
    val databaseUrl: String,
    val fetchInterval: Int?,
    val storeResponseBody: Boolean?,
    val storeResponseBodyOnError: Boolean?,
    val feeds: List<Feed>
) {
    data class Feed(
        val producer: String,
        val feed: String,
        val feedUrl: HttpUrl,
        val fetchInterval: Int?,
        val headers: Map<String, String>?,
        val basicAuthCredentials: BasicAuthCredential?,
        val ignoreSSLErrors: Boolean? = false,
        val queryParameters: Map<String, String>?,
        val extensions: List<GtfsRealtimeExtensions>? //would prefer an EnumSet here...
    ) {
        data class BasicAuthCredential(val username: String, val password: String)
    }
}

fun parseConfiguration(theConfiguration: Path): Configuration {
    val tomlDocument = TomlValue.from(theConfiguration)

    val tomlDecoder = TomlDecoder.default.with { _, tomlValue: TomlValue ->
            val urlString: String = tomlValue.decode()
            urlString.toHttpUrlOrNull()
        }.with { _, tomlValue: TomlValue ->
            val enumString: String = tomlValue.decode()
            GtfsRealtimeExtensions.valueOf(enumString)
        }

    return tomlDocument.decode(tomlDecoder)
}
