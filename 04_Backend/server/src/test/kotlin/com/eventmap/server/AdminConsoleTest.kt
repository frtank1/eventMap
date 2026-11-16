package com.eventmap.server

import com.eventmap.server.config.AppConfig
import com.eventmap.server.data.db.*
import com.eventmap.server.data.repository.BusinessRepositoryImpl
import com.eventmap.server.data.repository.QuestRepositoryImpl
import com.eventmap.server.data.venue.DbVenueCatalog
import com.eventmap.server.presentation.routes.adminRoutes
import com.eventmap.server.presentation.routes.adminVenueRoutes
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.*

class AdminConsoleTest {
    @Test fun shellHasSameSecurityPolicyAtBothEntrypoints() = testApplication {
        application { routing { adminVenueRoutes(DbVenueCatalog(), "test-only") } }
        val root = client.get("/admin")
        val plans = client.get("/admin/venues")
        assertEquals(root.bodyAsText(), plans.bodyAsText())
        for (path in listOf("/admin", "/admin/venues", "/admin/venues.js", "/admin/console.js", "/admin/venues.css")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
            assertTrue(response.headers["Content-Security-Policy"]!!.contains("script-src 'self'"))
            assertTrue(response.headers["Content-Security-Policy"]!!.contains("frame-ancestors 'none'"))
            assertFalse(response.bodyAsText().contains("test-only"))
        }
        val html = root.bodyAsText()
        assertTrue(html.contains("previewDialog")); assertTrue(html.contains("reviewDialog"))
        assertFalse(html.contains("onclick=")); assertFalse(html.contains("<style>"))
        val js = client.get("/admin/console.js").bodyAsText()
        assertFalse(js.contains("sessionStorage.setItem")); assertFalse(js.contains("localStorage"))
        assertFalse(js.contains("innerHTML")); assertFalse(js.contains("prompt("))
    }

    @Test fun moderationRequiresAuthenticationAndRejectsUnknownFilters() = testApplication {
        val db = Database.connect("jdbc:h2:mem:admin-console-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", driver="org.h2.Driver")
        transaction(db) { SchemaUtils.create(BusinessAccountsTable, BusinessDocumentsTable, QuestsTable) }
        application {
            install(ContentNegotiation) { json() }
            routing { adminRoutes(BusinessRepositoryImpl(), QuestRepositoryImpl(), "test-only") }
        }
        for (kind in listOf("business", "quests")) {
            assertEquals(HttpStatusCode.Forbidden, client.get("/admin/api/$kind").status)
            assertEquals(HttpStatusCode.Forbidden, client.get("/admin/api/$kind") { header("X-Admin-Key", "wrong") }.status)
            val valid = client.get("/admin/api/$kind") { header("X-Admin-Key", "test-only") }
            assertEquals(HttpStatusCode.OK, valid.status); assertEquals("[]", valid.bodyAsText())
            assertEquals("no-store", valid.headers[HttpHeaders.CacheControl])
            assertEquals(HttpStatusCode.BadRequest, client.get("/admin/api/$kind?status=unknown") { header("X-Admin-Key", "test-only") }.status)
        }
    }

    @Test fun productionCannotEnableDemoSeed() {
        val error = assertFailsWith<IllegalArgumentException> { AppConfig(appEnv="prod", demoSeedEnabled=true) }
        assertTrue(error.message!!.contains("SEED_DEMO_DATA"))
        assertFalse(AppConfig(appEnv="dev", demoSeedEnabled=false).demoSeedEnabled)
    }
}
