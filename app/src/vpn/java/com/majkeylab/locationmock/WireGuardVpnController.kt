package com.majkeylab.locationmock

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.GeneralSecurityException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class VpnUiState {
    NO_CONFIG,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    ERROR,
}

class WireGuardVpnController(context: Context) {
    private val appContext = context.applicationContext
    private val configFile = File(appContext.filesDir, CONFIG_FILE_NAME)
    private val storeDelegate = lazy { EncryptedVpnConfigStore(configFile, AndroidVpnKey.getOrCreate()) }
    private val backendDelegate = lazy { GoBackend(appContext) }
    private val mutex = Mutex()
    private val tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            state = when (newState) {
                Tunnel.State.UP -> VpnUiState.CONNECTED
                Tunnel.State.DOWN, Tunnel.State.TOGGLE -> inactiveState()
            }
            error = null
        }
    }

    @Volatile
    var state: VpnUiState = if (configFile.isFile) {
        VpnUiState.DISCONNECTED
    } else {
        VpnUiState.NO_CONFIG
    }
        private set

    @Volatile
    var error: String? = null
        private set

    @Volatile
    var hasConfiguration: Boolean = configFile.isFile
        private set

    fun permissionIntent(): Intent? = VpnService.prepare(appContext)

    suspend fun importConfiguration(input: InputStream): Result<Unit> = operation {
        check(state !in BUSY_OR_CONNECTED_STATES) { "Disconnect before replacing the configuration." }
        val bytes = input.readBounded(EncryptedVpnConfigStore.MAX_CONFIG_BYTES)
        try {
            parseWireGuardConfiguration(bytes)
            storeDelegate.value.write(bytes)
            hasConfiguration = true
            state = VpnUiState.DISCONNECTED
            error = null
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun connect(): Result<Unit> = operation {
        check(hasConfiguration) { "Import a WireGuard configuration first." }
        state = VpnUiState.CONNECTING
        val bytes = storeDelegate.value.read()
            ?: throw IllegalStateException("Import a WireGuard configuration first.")
        try {
            val config = parseWireGuardConfiguration(bytes)
            check(backendDelegate.value.setState(tunnel, Tunnel.State.UP, config) == Tunnel.State.UP) {
                "WireGuard connection failed."
            }
            state = VpnUiState.CONNECTED
            error = null
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun disconnect(): Result<Unit> = operation {
        state = VpnUiState.DISCONNECTING
        if (backendDelegate.isInitialized()) {
            backendDelegate.value.setState(tunnel, Tunnel.State.DOWN, null)
        }
        state = inactiveState()
        error = null
    }

    suspend fun removeConfiguration(): Result<Unit> = operation {
        if (backendDelegate.isInitialized()) {
            backendDelegate.value.setState(tunnel, Tunnel.State.DOWN, null)
        }
        if (storeDelegate.isInitialized()) {
            storeDelegate.value.clear()
        } else {
            Files.deleteIfExists(configFile.toPath())
        }
        hasConfiguration = false
        state = VpnUiState.NO_CONFIG
        error = null
    }

    private suspend fun operation(block: suspend () -> Unit): Result<Unit> = try {
        withContext(Dispatchers.IO) { mutex.withLock { block() } }
        Result.success(Unit)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        val message = safeMessage(cause)
        error = message
        state = if (hasConfiguration) VpnUiState.ERROR else VpnUiState.NO_CONFIG
        Result.failure(IllegalStateException(message))
    }

    private fun InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        var total = 0
        try {
            while (true) {
                val read = read(buffer)
                if (read == -1) break
                total += read
                require(total <= limit) { "WireGuard configuration is too large." }
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        } finally {
            buffer.fill(0)
        }
    }

    private fun inactiveState(): VpnUiState = if (hasConfiguration) {
        VpnUiState.DISCONNECTED
    } else {
        VpnUiState.NO_CONFIG
    }

    private fun safeMessage(cause: Exception): String = when (cause) {
        is IllegalArgumentException, is IllegalStateException ->
            cause.message ?: "WireGuard operation failed."
        is BadConfigException -> "Invalid WireGuard configuration."
        is GeneralSecurityException -> "Stored WireGuard configuration could not be decrypted."
        is IOException -> "Could not read or store the WireGuard configuration."
        is SecurityException -> "VPN permission is not available."
        else -> "WireGuard operation failed."
    }

    private companion object {
        const val CONFIG_FILE_NAME = "wireguard.conf.enc"
        const val TUNNEL_NAME = "LocationMock"
        const val READ_BUFFER_BYTES = 8_192

        val BUSY_OR_CONNECTED_STATES = setOf(
            VpnUiState.CONNECTED,
            VpnUiState.CONNECTING,
            VpnUiState.DISCONNECTING,
        )
    }
}

internal fun parseWireGuardConfiguration(bytes: ByteArray): Config {
    val config = Config.parse(ByteArrayInputStream(bytes))
    require(config.peers.isNotEmpty()) { "WireGuard configuration needs at least one peer." }
    require(config.peers.any { it.endpoint.isPresent && it.allowedIps.isNotEmpty() }) {
        "WireGuard configuration needs a peer endpoint and allowed IPs."
    }
    return config
}
