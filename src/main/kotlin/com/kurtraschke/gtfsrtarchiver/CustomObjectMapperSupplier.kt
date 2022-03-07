package com.kurtraschke.gtfsrtarchiver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.guava.GuavaModule
import com.vladmihalcea.hibernate.type.util.ObjectMapperSupplier


class CustomObjectMapperSupplier : ObjectMapperSupplier {
    override fun get(): ObjectMapper {
        val objectMapper = ObjectMapper().findAndRegisterModules()

        objectMapper.registerModule(GuavaModule())

        return objectMapper
    }
}
