package com.kurtraschke.gtfsrtarchiver.archiver

import com.google.common.io.CharStreams
import org.hibernate.tool.hbm2ddl.ImportSqlCommandExtractor
import org.postgresql.core.Parser.parseJdbcSql
import java.io.Reader

class PostgresqlCommandExtractor : ImportSqlCommandExtractor {
    override fun extractCommands(reader: Reader): Array<String> {
        val parsedStatements = parseJdbcSql(
            CharStreams.toString(reader), true, false, true, false, true
        )
        return parsedStatements.map { it.nativeSql }.toTypedArray()
    }
}
