package com.eventmap.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppLockStorageTest {

    private fun storage() = AppLockStorage(FakeSecureStorage())

    @Test
    fun pinNotSetByDefault() {
        assertFalse(storage().isPinSet())
    }

    @Test
    fun setAndVerifyPin() {
        val s = storage()
        s.setPin("1234")
        assertTrue(s.isPinSet())
        assertTrue(s.verifyPin("1234"))
        assertFalse(s.verifyPin("0000"))
    }

    @Test
    fun pinNotStoredInPlaintext() {
        val backing = FakeSecureStorage()
        AppLockStorage(backing).setPin("1234")
        // хеш не должен содержать сам PIN
        val raw = backing.getString("applock_pin")
        assertNotNull(raw)
        assertFalse(raw!!.contains("1234"))
    }

    @Test
    fun differentSaltsProduceDifferentHashes() {
        val a = FakeSecureStorage().also { AppLockStorage(it).setPin("1234") }.getString("applock_pin")
        val b = FakeSecureStorage().also { AppLockStorage(it).setPin("1234") }.getString("applock_pin")
        assertTrue(a != b) // соль случайная
    }

    @Test
    fun biometricFlag() {
        val s = storage()
        assertFalse(s.isBiometricEnabled())
        s.setBiometricEnabled(true)
        assertTrue(s.isBiometricEnabled())
        s.setBiometricEnabled(false)
        assertFalse(s.isBiometricEnabled())
    }

    @Test
    fun credentialsRoundTrip() {
        val s = storage()
        assertNull(s.credentials())
        assertFalse(s.hasCredentials())
        s.saveCredentials("me@example.com", "secret")
        assertTrue(s.hasCredentials())
        assertEquals(AppLockStorage.Credentials("me@example.com", "secret"), s.credentials())
    }

    @Test
    fun clearAllWipesEverything() {
        val s = storage()
        s.setPin("1234")
        s.setBiometricEnabled(true)
        s.saveCredentials("me@example.com", "secret")

        s.clearAll()

        assertFalse(s.isPinSet())
        assertFalse(s.isBiometricEnabled())
        assertNull(s.credentials())
    }
}
