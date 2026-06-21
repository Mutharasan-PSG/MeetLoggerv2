package com.meetloggerv2.core.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppGateTest {

    private fun config(
        minSupportedVersion: Int = 1,
        maintenanceMode: Boolean = false,
        maintenanceMessage: String = "down",
        blockedUserIds: Set<String> = emptySet(),
        updateUrl: String = "https://play/app",
    ) = GateConfig(
        minSupportedVersion = minSupportedVersion,
        maintenanceMode = maintenanceMode,
        maintenanceMessage = maintenanceMessage,
        blockedUserIds = blockedUserIds,
        updateUrl = updateUrl,
    )

    @Test
    fun defaults_allow() {
        val result = AppGate.evaluate(config(), currentVersionCode = 1, userId = "u1")
        assertEquals(GateResult.Allowed, result)
    }

    @Test
    fun belowMinVersion_forcesUpdate() {
        val result = AppGate.evaluate(
            config(minSupportedVersion = 5, updateUrl = "https://store/x"),
            currentVersionCode = 4,
            userId = "u1",
        )
        assertTrue(result is GateResult.ForceUpdate)
        assertEquals("https://store/x", (result as GateResult.ForceUpdate).updateUrl)
    }

    @Test
    fun atOrAboveMinVersion_notForced() {
        assertEquals(
            GateResult.Allowed,
            AppGate.evaluate(config(minSupportedVersion = 5), currentVersionCode = 5, userId = "u1"),
        )
        assertEquals(
            GateResult.Allowed,
            AppGate.evaluate(config(minSupportedVersion = 5), currentVersionCode = 9, userId = "u1"),
        )
    }

    @Test
    fun blockedUid_isBlocked() {
        val result = AppGate.evaluate(
            config(blockedUserIds = setOf("bad1", "bad2")),
            currentVersionCode = 1,
            userId = "bad2",
        )
        assertEquals(GateResult.Blocked, result)
    }

    @Test
    fun nonBlockedUid_allowed() {
        val result = AppGate.evaluate(
            config(blockedUserIds = setOf("bad1")),
            currentVersionCode = 1,
            userId = "good",
        )
        assertEquals(GateResult.Allowed, result)
    }

    @Test
    fun nullOrBlankUid_neverBlocked() {
        assertEquals(
            GateResult.Allowed,
            AppGate.evaluate(config(blockedUserIds = setOf("")), currentVersionCode = 1, userId = null),
        )
        assertEquals(
            GateResult.Allowed,
            AppGate.evaluate(config(blockedUserIds = setOf("x")), currentVersionCode = 1, userId = "  "),
        )
    }

    @Test
    fun maintenance_whenFlagOn() {
        val result = AppGate.evaluate(
            config(maintenanceMode = true, maintenanceMessage = "brb"),
            currentVersionCode = 1,
            userId = "u1",
        )
        assertTrue(result is GateResult.Maintenance)
        assertEquals("brb", (result as GateResult.Maintenance).message)
    }

    @Test
    fun priority_forceUpdate_beatsBlock_andMaintenance() {
        val result = AppGate.evaluate(
            config(minSupportedVersion = 5, maintenanceMode = true, blockedUserIds = setOf("u1")),
            currentVersionCode = 1,
            userId = "u1",
        )
        assertTrue(result is GateResult.ForceUpdate)
    }

    @Test
    fun priority_block_beatsMaintenance() {
        val result = AppGate.evaluate(
            config(maintenanceMode = true, blockedUserIds = setOf("u1")),
            currentVersionCode = 1,
            userId = "u1",
        )
        assertEquals(GateResult.Blocked, result)
    }
}
