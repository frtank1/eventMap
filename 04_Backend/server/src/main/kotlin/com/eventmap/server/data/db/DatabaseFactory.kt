package com.eventmap.server.data.db

import com.eventmap.server.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

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
        transaction {
            if (EventsTable.selectAll().empty()) seed()
        }
    }

    private fun seed() {
        val org = "11111111-1111-1111-1111-111111111111"
        OrganizersTable.insert {
            it[id] = org; it[name] = "Astana Live"
            it[bio] = "Концерты и опен-эйры в центре Астаны"
            it[isVerified] = true; it[subscribers] = 1840
        }
        data class E(
            val id: String, val title: String, val cat: String, val st: String,
            val la: Double, val ln: Double, val loc: String,
            val pt: String, val pa: Int, val going: Int, val live: Int, val org: String?,
        )
        listOf(
            E("1", "Концерт у Байтерека", "Концерт", "live", 51.1283, 71.4306, "Байтерек, Астана", "paid", 3000, 48, 128, org),
            E("2", "Астана — Кайрат", "Спорт", "live", 51.1083, 71.4156, "Астана Арена", "paid", 2000, 540, 540, org),
            E("3", "Йога в парке", "Йога", "soon", 51.1380, 71.4100, "Парк у Хан Шатыра", "free", 0, 0, 0, null),
            E("5", "Квиз «Мозгобойня»", "Квиз", "soon", 51.1450, 71.4300, "Бар в центре", "paid", 2500, 0, 0, null),
            E("6", "Открытая лекция: AI и город", "Лекция", "soon", 51.0905, 71.4030, "Назарбаев Университет", "free", 0, 0, 0, null),
        ).forEach { e ->
            EventsTable.insert {
                it[id] = e.id; it[title] = e.title; it[category] = e.cat; it[status] = e.st
                it[lat] = e.la; it[lng] = e.ln; it[locationName] = e.loc
                it[priceType] = e.pt; it[priceAmount] = e.pa
                it[goingCount] = e.going; it[liveViewers] = e.live; it[organizerId] = e.org
            }
        }
    }
}
