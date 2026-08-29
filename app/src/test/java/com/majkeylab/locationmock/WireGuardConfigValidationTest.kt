package com.majkeylab.locationmock

import com.wireguard.crypto.KeyPair
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WireGuardConfigValidationTest {
    @Test
    fun acceptsPeerWithEndpointAndAllowedIps() {
        val config = parseWireGuardConfiguration(validConfig().encodeToByteArray())

        assertEquals(1, config.peers.size)
    }

    @Test
    fun rejectsPeerWithoutEndpoint() {
        val config = validConfig().lineSequence()
            .filterNot { it.startsWith("Endpoint") }
            .joinToString("\n")

        assertEquals(
            "WireGuard configuration needs a peer endpoint and allowed IPs.",
            assertFailsWith<IllegalArgumentException> {
                parseWireGuardConfiguration(config.encodeToByteArray())
            }.message,
        )
    }

    private fun validConfig(): String {
        val interfaceKeys = KeyPair()
        val peerKeys = KeyPair()
        return """
            [Interface]
            PrivateKey = ${interfaceKeys.privateKey.toBase64()}
            Address = 10.0.0.2/32

            [Peer]
            PublicKey = ${peerKeys.publicKey.toBase64()}
            Endpoint = 127.0.0.1:51820
            AllowedIPs = 0.0.0.0/0
        """.trimIndent()
    }
}
