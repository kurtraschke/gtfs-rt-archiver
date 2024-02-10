package com.kurtraschke.gtfsrtarchiver.api

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.time.temporal.ChronoUnit

fun timestampForFilename(ts: Instant): String = ts.toJavaInstant().truncatedTo(ChronoUnit.SECONDS)
    .toString()
    .replace("-", "")
    .replace(":", "")
