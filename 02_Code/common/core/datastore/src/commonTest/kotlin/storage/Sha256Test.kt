package com.eventmap.core.datastore

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    private fun ByteArray.hex(): String =
        joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun knownVectorAbc() {
        // FIPS 180-4: SHA-256("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hash("abc".encodeToByteArray()).hex(),
        )
    }

    @Test
    fun knownVectorEmpty() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hash(ByteArray(0)).hex(),
        )
    }

    @Test
    fun knownVectorLongerThanOneBlock() {
        // 56 байт — кейс, проверяющий паддинг на границе блока
        val input = "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hash(input.encodeToByteArray()).hex(),
        )
    }
}
