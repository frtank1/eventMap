package com.eventmap.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStorageTest {

    private fun storage() = TokenStorage(FakeSecureStorage())

    @Test
    fun saveAndReadActiveSession() {
        val ts = storage()
        ts.saveTokens(UserType.INTERNAL, access = "acc", refresh = "ref")

        assertEquals(UserType.INTERNAL, ts.getActiveUser())
        assertEquals("acc", ts.getActiveAccessToken())
        assertEquals(AuthTokens("acc", "ref"), ts.getActiveSession())
        assertTrue(ts.isAuthorized())
    }

    @Test
    fun internalAndExternalSessionsAreSeparate() {
        val ts = storage()
        ts.saveTokens(UserType.INTERNAL, "in", "in-r")
        ts.saveTokens(UserType.EXTERNAL, "ex", "ex-r")

        // активным стал последний — EXTERNAL
        assertEquals("ex", ts.getActiveAccessToken())

        // переключаемся на INTERNAL — старые токены на месте
        ts.setActiveUser(UserType.INTERNAL)
        assertEquals("in", ts.getActiveAccessToken())
    }

    @Test
    fun logoutClearsActiveSession() {
        val ts = storage()
        ts.saveTokens(UserType.EXTERNAL, "acc", "ref")

        ts.logout()

        assertEquals(UserType.NONE, ts.getActiveUser())
        assertNull(ts.getActiveSession())
        assertEquals("", ts.getActiveAccessToken())
        assertFalse(ts.isAuthorized())
    }

    @Test
    fun emptyStorageIsUnauthorized() {
        val ts = storage()
        assertFalse(ts.isAuthorized())
        assertNull(ts.getActiveSession())
    }
}
