package com.eventmap.server

import com.eventmap.server.data.venue.*
import com.eventmap.server.presentation.routes.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

/** Explicit local-only harness. Uses real editor/routes with its own persistent H2 file, no production services. */
fun main() {
    val folder=File("build/venue-editor-qa").apply { mkdirs() }
    val database=Database.connect("jdbc:h2:file:${folder.absolutePath}/plans;MODE=PostgreSQL",driver="org.h2.Driver")
    transaction(database) { SchemaUtils.create(VenuePlansTable,VenueRevisionsTable) }
    val editor=DbVenueCatalog(database=database)
    embeddedServer(Netty,host="127.0.0.1",port=4891) {
        install(ContentNegotiation){json(Json{encodeDefaults=true})}
        routing { adminVenueRoutes(editor,"local-venue-test-only");venueRoutes(editor) }
    }.start(wait=true)
}
