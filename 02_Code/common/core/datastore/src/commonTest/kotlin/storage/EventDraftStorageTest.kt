package com.eventmap.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.io.encoding.Base64

class EventDraftStorageTest {
    @Test fun draftsAreIsolatedAndSurviveReturningToAccount() {
        val storage = FakeSecureStorage()
        var owner: String? = "alice"
        val drafts = EventDraftStorage(storage) { owner }
        drafts.save("alice form")
        owner = "bob"
        assertNull(drafts.load())
        drafts.save("bob form")
        drafts.save("late callback from alice", "alice")
        assertEquals("bob form", drafts.load())
        owner = "alice"
        assertEquals("alice form", drafts.load())
        drafts.clear()
        assertNull(drafts.load())
        owner = "bob"
        assertEquals("bob form", drafts.load())
    }

    @Test fun guestsDoNotReadLegacyOrWriteSharedDrafts() {
        val storage = FakeSecureStorage()
        storage.putString("create_event_draft_v1", "unknown owner")
        val drafts = EventDraftStorage(storage) { null }
        drafts.save("guest")
        assertNull(drafts.load())
        assertNull(EventDraftStorage(storage) { "alice" }.load())
    }

    @Test fun accountNamespaceUsesStableClaimRatherThanTokenValue() {
        val tokens = TokenStorage(FakeSecureStorage())
        fun token(payload: String) = "header.${Base64.UrlSafe.encode(payload.encodeToByteArray())}.signature"
        tokens.saveTokens(UserType.INTERNAL, token("{\"userId\":\"alice\",\"exp\":1}"), "refresh")
        assertEquals("alice", tokens.activeAccountId())
        tokens.saveTokens(UserType.INTERNAL, token("{\"userId\":\"alice\",\"exp\":2}"), "new refresh")
        assertEquals("alice", tokens.activeAccountId())
        tokens.logout()
        assertNull(tokens.activeAccountId())
        tokens.saveTokens(UserType.INTERNAL, "invalid", "refresh")
        assertNull(tokens.activeAccountId())
    }
}
