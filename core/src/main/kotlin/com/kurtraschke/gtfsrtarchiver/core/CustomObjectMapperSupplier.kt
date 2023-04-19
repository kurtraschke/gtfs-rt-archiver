package com.kurtraschke.gtfsrtarchiver.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier

class CustomObjectMapperSupplier : ObjectMapperSupplier {
    override fun get(): ObjectMapper {
        val objectMapper = ObjectMapper().findAndRegisterModules()

        objectMapper.registerModule(GuavaModule())

        return objectMapper
    }
}
