package com.eventmap.core.datastore

import kotlin.random.Random

/**
 * Хранилище блокировки приложения (App Lock) поверх зашифрованного vault.
 *
 * Хранит:
 *  - PIN в виде `соль:итерации:хеш` (открытый PIN никогда не пишется);
 *  - флаг включённой биометрии;
 *  - учётные данные для ТИХОГО РЕ-ЛОГИНА (email + пароль).
 *
 * БЕЗОПАСНОСТЬ: vault (KSafe без PLAIN_TEXT) шифруется на уровне ОС
 * (Android Keystore / iOS Keychain). Хранение пароля — осознанный компромисс
 * ради тихого ре-логина при умершем refresh-токене (выбор пользователя).
 * TODO когда бэкенд даст device-bound long-lived token — заменить пароль на него.
 */
class AppLockStorage(
    private val storage: SecureStorage,
) {
    // --- PIN ---

    fun isPinSet(): Boolean = !storage.getString(KEY_PIN).isNullOrBlank()

    fun setPin(pin: String) {
        val salt = randomHex(SALT_BYTES)
        val hash = derive(pin, salt, ITERATIONS)
        storage.putString(KEY_PIN, "$salt:$ITERATIONS:$hash")
    }

    /** Проверка PIN с защитой от утечки времени сравнения. */
    fun verifyPin(pin: String): Boolean {
        val stored = storage.getString(KEY_PIN) ?: return false
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val salt = parts[0]
        val iterations = parts[1].toIntOrNull() ?: return false
        val expected = parts[2]
        return constantTimeEquals(derive(pin, salt, iterations), expected)
    }

    fun clearPin() = storage.remove(KEY_PIN)

    // --- Биометрия ---

    fun isBiometricEnabled(): Boolean = storage.getString(KEY_BIO) == "1"

    fun setBiometricEnabled(enabled: Boolean) =
        storage.putString(KEY_BIO, if (enabled) "1" else "0")

    // --- Учётка для тихого ре-логина ---

    fun saveCredentials(email: String, password: String) {
        storage.putString(KEY_EMAIL, email)
        storage.putString(KEY_PASSWORD, password)
    }

    fun credentials(): Credentials? {
        val email = storage.getString(KEY_EMAIL)
        val password = storage.getString(KEY_PASSWORD)
        return if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
            Credentials(email, password)
        } else {
            null
        }
    }

    fun hasCredentials(): Boolean = credentials() != null

    /** Полный сброс App Lock — на реальном логауте. */
    fun clearAll() {
        storage.remove(KEY_PIN)
        storage.remove(KEY_BIO)
        storage.remove(KEY_EMAIL)
        storage.remove(KEY_PASSWORD)
    }

    // --- helpers ---

    private fun derive(pin: String, saltHex: String, iterations: Int): String {
        var data = (saltHex + pin).encodeToByteArray()
        repeat(iterations) { data = Sha256.hash(data) }
        return data.toHex()
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        Random.nextBytes(b)
        return b.toHex()
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    data class Credentials(val email: String, val password: String)

    companion object {
        private const val KEY_PIN = "applock_pin"
        private const val KEY_BIO = "applock_bio"
        private const val KEY_EMAIL = "applock_cred_email"
        private const val KEY_PASSWORD = "applock_cred_password"
        private const val ITERATIONS = 10_000
        private const val SALT_BYTES = 16
    }
}

private val HEX = "0123456789abcdef".toCharArray()

private fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (byte in this) {
        val v = byte.toInt() and 0xff
        sb.append(HEX[v ushr 4])
        sb.append(HEX[v and 0x0f])
    }
    return sb.toString()
}
