package com.eventmap.server

import com.eventmap.server.data.db.*
import com.eventmap.server.data.repository.MediaRepositoryImpl
import com.eventmap.server.data.repository.VideoRepositoryImpl
import com.eventmap.server.data.video.*
import com.eventmap.server.domain.repository.*
import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.*

class MuxRepositoryTest {
    private val uploads = MuxUploadRepositoryImpl()
    private val media = MediaRepositoryImpl()
    private val gateway = MuxGatewayImpl(muxTestConfig())

    @BeforeTest fun database() { createMuxTestDatabase() }

    @Test fun readyBeforeConfirmAndDuplicateWebhookAreSafe() = runTest {
        uploads.reserve("mux-m", "owner", "public", "video/mp4", 20)
        val raw = muxReady("evt-1", "mux-m")
        val event = gateway.verifyWebhook(raw, muxSignature(raw))
        uploads.apply(event, 120)
        uploads.apply(event, 120)
        uploads.attachUpload("mux-m", "upload-1")
        assertEquals("ready", uploads.confirm("mux-m", "owner", "public", "video/mp4").status)
        uploads.publish("mux-m", "owner", "public", "First caption")
        uploads.publish("mux-m", "owner", "public", "Retry caption")
        transaction {
            assertEquals(1L, MuxWebhookEventsTable.selectAll().count())
            assertEquals(1L, VideosTable.selectAll().count())
            assertEquals("First caption", VideosTable.selectAll().single()[VideosTable.caption])
        }
        uploads.apply(event.copy(id = "late-created", type = "video.upload.asset_created", playbackId = null), 120)
        assertEquals("ready", media.byId("mux-m")!!.status)
    }

    @Test fun processingIsNotInFeedAndPrivateVideoNeverEntersPublicFeed() = runTest {
        for (eventId in listOf("public", "private")) {
            val id = "mux-$eventId"
            uploads.reserve(id, "owner", eventId, "video/mp4", 20)
            uploads.confirm(id, "owner", eventId, "video/mp4")
            assertEquals("processing", uploads.publish(id, "owner", eventId, "caption").status)
        }
        assertTrue(VideoRepositoryImpl().feedNow("owner").isEmpty())
        for (eventId in listOf("public", "private")) {
            uploads.apply(MuxEvent("ready-$eventId", "video.asset.ready", "mux-$eventId", null,
                "asset-$eventId", "play-$eventId", 20.0), 120)
        }
        assertEquals(listOf("public"), VideoRepositoryImpl().feedNow(null).map { it.eventId })
        assertEquals(listOf("public"), VideoRepositoryImpl().feedNow("owner").map { it.eventId })
    }

    @Test fun ownerEventAndQuotaAreEnforced() = runTest {
        uploads.reserve("mux-m", "owner", "public", "video/mp4", 1)
        assertEquals(429, assertFailsWith<MediaFailure> { uploads.reserve("mux-n", "owner", "public", "video/mp4", 1) }.status)
        assertEquals(403, assertFailsWith<MediaFailure> { uploads.confirm("mux-m", "outsider", "public", "video/mp4") }.status)
        assertEquals(403, assertFailsWith<MediaFailure> { uploads.confirm("mux-m", "owner", "private", "video/mp4") }.status)
        assertEquals(409, assertFailsWith<MediaFailure> { uploads.publish("mux-m", "owner", "public", "caption") }.status)
    }

    @Test fun durationLimitAndDeletedAssetCannotBeRepublishedByOldReadyEvent() = runTest {
        uploads.reserve("mux-m", "owner", "public", "video/mp4", 20)
        val tooLong = MuxEvent("long", "video.asset.ready", "mux-m", null, "asset", "play", 121.0)
        uploads.apply(tooLong, 120)
        assertEquals("errored", media.byId("mux-m")!!.status)
        uploads.apply(tooLong.copy(id = "old-ready", duration = 10.0), 120)
        assertEquals("errored", media.byId("mux-m")!!.status)
        assertNull(media.byId("mux-m")!!.playbackId)
    }

    @Test fun privateAccessIsNotGrantedBySelfAttendance() = runTest {
        val access = MediaAccessImpl()
        assertTrue(access.canView("public", null))
        assertFalse(access.canView("private", null))
        assertTrue(access.canView("private", "owner"))
        assertFalse(access.canView("private", "outsider"))
        transaction {
            AttendanceTable.insert { it[userId] = "outsider"; it[eventId] = "private"; it[type] = "going" }
        }
        assertFalse(access.canView("private", "outsider"))
        transaction {
            EventJoinRequestsTable.insert { it[userId] = "outsider"; it[eventId] = "private"; it[status] = "approved" }
        }
        assertTrue(access.canView("private", "outsider"))
    }

    @Test fun invitedMemberCanViewButDeclinedMemberCannot() = runTest {
        transaction {
            InvitationsTable.insert { it[id] = "invite"; it[eventId] = "private"; it[templateId] = "t" }
            InvitationGuestsTable.insert { it[id] = "g"; it[invitationId] = "invite"; it[userId] = "outsider" }
        }
        assertTrue(MediaAccessImpl().canView("private", "outsider"))
        transaction { InvitationGuestsTable.update({ InvitationGuestsTable.id eq "g" }) { it[rsvp] = "no" } }
        assertFalse(MediaAccessImpl().canView("private", "outsider"))
    }
}

internal fun createMuxTestDatabase() {
    Database.connect("jdbc:h2:mem:mux-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction {
        SchemaUtils.create(UsersTable, EventsTable, MediaTable, MuxUploadsTable, MuxWebhookEventsTable, VideosTable,
            VideoLikesTable, EventJoinRequestsTable, InvitationsTable, InvitationGuestsTable, AttendanceTable)
        for (uid in listOf("owner", "outsider")) UsersTable.insert {
            it[id] = uid; it[email] = "$uid@example.invalid"; it[name] = uid; it[passwordHash] = "not-a-login"
        }
        for (eid in listOf("public", "private")) EventsTable.insert {
            it[id] = eid; it[title] = eid; it[organizerId] = "owner"; it[visibility] = eid
        }
    }
}
