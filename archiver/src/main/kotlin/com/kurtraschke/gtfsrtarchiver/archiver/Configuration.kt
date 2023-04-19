package com.kurtraschke.gtfsrtarchiver.archiver

import cc.ekblad.toml.decode
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import com.kurtraschke.gtfsrtarchiver.core.GtfsRealtimeExtensions
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
    val mapper = tomlMapper {
        decoder { it: TomlValue.String ->
            val urlString: String = it.value
            urlString.toHttpUrlOrNull()
        }
        decoder { it: TomlValue.String ->
            val enumString: String = it.value
            GtfsRealtimeExtensions.valueOf(enumString)
        }
    }

    return mapper.decode(theConfiguration)
}
