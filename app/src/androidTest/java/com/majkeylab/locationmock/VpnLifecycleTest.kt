package com.majkeylab.locationmock

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertSame
import org.junit.Test

class VpnLifecycleTest {
    @Test
    fun controllerSurvivesActivityRecreationAndRelaunch() {
        lateinit var original: WireGuardVpnController
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { original = WireGuardVpnController.getInstance(it) }
            scenario.recreate()
            scenario.onActivity { assertSame(original, WireGuardVpnController.getInstance(it)) }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertSame(original, WireGuardVpnController.getInstance(it)) }
        }
    }
}
