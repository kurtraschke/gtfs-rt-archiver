package com.kurtraschke.gtfsrtarchiver.archiver.modules

import com.google.inject.name.Names
import dev.misfitlabs.kotlinguice4.KotlinModule
import java.util.*

class ProjectVersionModule : KotlinModule() {
    override fun configure() {
        val properties = Properties()
        ProjectVersionModule::class.java.getResourceAsStream("/maven-version.properties").use { properties.load(it) }

        Names.bindProperties(binder(), properties)
    }
}