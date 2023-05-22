package com.kurtraschke.gtfsrtarchiver.archiver

import com.google.common.io.CharStreams
import org.hibernate.dialect.Dialect
import org.hibernate.tool.schema.spi.SqlScriptCommandExtractor
import org.postgresql.core.Parser.parseJdbcSql
import java.io.Reader

class PostgresqlCommandExtractor : SqlScriptCommandExtractor {
    override fun extractCommands(reader: Reader, d: Dialect): List<String> {
        val parsedStatements = parseJdbcSql(
            CharStreams.toString(reader), true, false, true, false, true
        )
        return parsedStatements.map { it.nativeSql }.toList()
    }
}
