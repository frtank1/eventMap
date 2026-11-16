package com.eventmap.server

import com.eventmap.server.data.venue.DbVenueCatalog
import com.eventmap.server.domain.model.*
import com.eventmap.server.domain.repository.VenueEditError
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.sql.DriverManager
import kotlin.test.*

/** Opt-in release gate. Only a fresh, explicitly named loopback QA database is accepted. */
class VenuePostgresMigrationTest {
    @Test fun upgradePreservesEventsAndPublicationsReachExistingReader() = runBlocking {
        val url = System.getenv("EVENTMAP_VENUE_PG_TEST_URL")
        assumeTrue(url != null, "Set EVENTMAP_VENUE_PG_TEST_URL for the isolated PostgreSQL release check")
        require(url != null && Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/venue_release_qa").matches(url))
        val user = "venue_qa"
        val password = "local-venue-qa-only"
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'").use {
                    assertTrue(it.next())
                    assertEquals(0, it.getInt(1), "Use a new disposable database; this test never clears existing data")
                }
            }
        }
        fun flyway(target: String) = Flyway.configure().dataSource(url, user, password)
            .locations("classpath:db/migration").target(target).cleanDisabled(true).load()
        assertEquals(14, flyway("14").migrate().migrationsExecuted)
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.prepareStatement("INSERT INTO events(id,title) VALUES (?,?)").use {
                it.setString(1, "venue-migration-existing-event")
                it.setString(2, "Existing event must survive")
                it.executeUpdate()
            }
        }
        assertEquals(2, flyway("16").migrate().migrationsExecuted)
        assertEquals(0, flyway("16").migrate().migrationsExecuted)
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT title,building_id,floor_id,room_id FROM events WHERE id='venue-migration-existing-event'").use {
                    assertTrue(it.next()); assertEquals("Existing event must survive", it.getString(1))
                    for (column in 2..4) assertNull(it.getString(column))
                }
                assertFailsWith<java.sql.SQLException> {
                    statement.executeUpdate("UPDATE events SET room_id='room' WHERE id='venue-migration-existing-event'")
                }
                statement.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success=true").use {
                    assertTrue(it.next()); assertEquals(16, it.getInt(1))
                }
            }
        }
        val database = Database.connect(url, driver="org.postgresql.Driver", user=user, password=password)
        val admin = DbVenueCatalog(database=database)
        val existingReader = DbVenueCatalog(database=database)
        val id = "osm:way:999999992"
        val first = VenueBuilding(buildingId=id, revision="draft", name="LOCAL QA ONLY",
            floors=listOf(VenueFloor("l1", "First floor", 1, VenuePlan(100.0, 60.0),
                listOf(VenueRoom("hall", "Hall", listOf(PlanPoint(5.0,5.0), PlanPoint(50.0,5.0), PlanPoint(50.0,50.0)))))))
        assertTrue(existingReader.building(id)!!.floors.isEmpty())
        admin.save(id, 0, first)
        assertTrue(existingReader.building(id)!!.floors.isEmpty(), "Draft must remain private")
        val published = admin.publish(id, 1).published!!
        assertEquals(published, existingReader.building(id))
        val second = first.copy(floors=first.floors + VenueFloor("l2", "Second floor", 2))
        admin.save(id, 2, second)
        assertEquals(published, existingReader.building(id))
        val updated = admin.publish(id, 3).published!!
        assertNotEquals(published.revision, updated.revision)
        assertEquals(second.floors, existingReader.building(id)!!.floors)
        assertEquals(updated, DbVenueCatalog(database=database).building(id))
        assertEquals(published, admin.revision(id, published.revision))
        assertEquals("VERSION_CONFLICT", assertFailsWith<VenueEditError> { admin.publish(id, 3) }.code)
        assertEquals(2, admin.editor(id)!!.history.size)
    }
}
