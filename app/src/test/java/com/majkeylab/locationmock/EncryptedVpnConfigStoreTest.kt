package com.majkeylab.locationmock

import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class EncryptedVpnConfigStoreTest {
    private lateinit var file: File
    private lateinit var store: EncryptedVpnConfigStore

    @Before
    fun setUp() {
        file = File.createTempFile("location-mock-vpn", ".bin").also(File::delete)
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        store = EncryptedVpnConfigStore(file, key)
    }

    @After
    fun tearDown() {
        file.delete()
        File("${file.path}.tmp").delete()
    }

    @Test
    fun roundTripsEncryptedBytes() {
        val cleartext = "[Interface]\nPrivateKey = test".encodeToByteArray()

        store.write(cleartext)

        assertContentEquals(cleartext, store.read())
        assertFalse(file.readBytes().contentEquals(cleartext))
    }

    @Test
    fun rejectsTamperedCiphertext() {
        store.write("secret".encodeToByteArray())
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last() + 1).toByte()
        file.writeBytes(bytes)

        assertFailsWith<GeneralSecurityException> { store.read() }
    }

    @Test
    fun clearDeletesEncryptedFile() {
        store.write("secret".encodeToByteArray())

        store.clear()

        assertNull(store.read())
    }
}
