package com.kurtraschke.gtfsrtarchiver.archiver.modules

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import java.util.*

class ProjectVersionModule : AbstractModule() {
    override fun configure() {
        val properties = Properties()
        ProjectVersionModule::class.java.getResourceAsStream("/maven-version.properties").use { properties.load(it) }

        Names.bindProperties(binder(), properties)
    }
}