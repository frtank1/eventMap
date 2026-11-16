package com.eventmap.server.data.db

import com.eventmap.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {
    fun init(config: AppConfig) {
        val hikari = HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 6
        }
        val dataSource = HikariDataSource(hikari)

        // Схема — только через Flyway-миграции (src/main/resources/db/migration).
        // baselineOnMigrate: существующие dev-базы (созданные ранее SchemaUtils)
        // получают baseline=0, и V1 применяется поверх (она idempotent: IF NOT EXISTS).
        Flyway.configure()
            .dataSource(dataSource)
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate()

        Database.connect(dataSource)
    }
}
