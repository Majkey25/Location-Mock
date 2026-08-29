package com.majkeylab.locationmock

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedVpnConfigStore(
    private val file: File,
    private val key: SecretKey,
) {
    fun write(cleartext: ByteArray) {
        require(cleartext.isNotEmpty()) { "WireGuard configuration is empty." }
        require(cleartext.size <= MAX_CONFIG_BYTES) { "WireGuard configuration is too large." }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val ciphertext = cipher.doFinal(cleartext)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            val output = FileOutputStream(temporary)
            DataOutputStream(BufferedOutputStream(output)).use { data ->
                data.writeInt(MAGIC)
                data.writeByte(FORMAT_VERSION)
                data.writeByte(cipher.iv.size)
                data.write(cipher.iv)
                data.write(ciphertext)
                data.flush()
                output.fd.sync()
            }
            replace(temporary)
        } finally {
            temporary.delete()
        }
    }

    fun read(): ByteArray? {
        if (!file.isFile) return null
        if (file.length() !in MIN_FILE_BYTES..MAX_FILE_BYTES) {
            throw IOException("Encrypted WireGuard configuration has an invalid size.")
        }
        DataInputStream(FileInputStream(file)).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Invalid WireGuard configuration header.")
            if (input.readUnsignedByte() != FORMAT_VERSION) {
                throw IOException("Unsupported WireGuard configuration format.")
            }
            val ivLength = input.readUnsignedByte()
            if (ivLength !in MIN_IV_BYTES..MAX_IV_BYTES) {
                throw IOException("Invalid WireGuard configuration IV.")
            }
            val iv = ByteArray(ivLength).also(input::readFully)
            val ciphertext = input.readBytes()
            if (ciphertext.isEmpty()) throw IOException("Encrypted WireGuard configuration is empty.")
            return Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                doFinal(ciphertext)
            }
        }
    }

    fun clear() {
        Files.deleteIfExists(file.toPath())
        Files.deleteIfExists(File(file.parentFile, "${file.name}.tmp").toPath())
    }

    private fun replace(temporary: File) {
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        const val MAX_CONFIG_BYTES = 262_144

        private const val MAGIC = 0x4C4D5747
        private const val FORMAT_VERSION = 1
        private const val MIN_IV_BYTES = 12
        private const val MAX_IV_BYTES = 16
        private const val GCM_TAG_BITS = 128
        private const val MIN_FILE_BYTES = 4L + 1L + 1L + MIN_IV_BYTES + 16L
        private const val MAX_FILE_BYTES = 262_200L
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

object AndroidVpnKey {
    fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let {
            return it
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "location_mock_wireguard"
}
