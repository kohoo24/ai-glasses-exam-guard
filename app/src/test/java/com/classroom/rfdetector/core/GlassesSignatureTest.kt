package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Test

class GlassesSignatureTest {
    private fun ble(name: String = "", ids: Set<Int> = emptySet(), hints: Set<SignalHint> = emptySet()) =
        glassesEvidence(ScanSource.BLE, "AA:BB:CC:DD:EE:FF", name, ids, hints)

    @Test fun eyewearCompanyCodesAreStrong() {
        assertEquals(GlassesConfidence.STRONG, ble(ids = setOf(0x0D53))!!.confidence) // Luxottica: Ray-Ban Meta, Oakley Meta
        assertEquals(GlassesConfidence.STRONG, ble(ids = setOf(0x03C2))!!.confidence) // Snap Spectacles
        assertEquals(GlassesConfidence.STRONG, ble(ids = setOf(0x10F9))!!.confidence) // Even Realities
    }

    @Test fun metaCompanyCodesAreOnlyPossibleBecauseQuestUsesThemToo() {
        val meta = ble(ids = setOf(0x01AB))!!
        assertEquals(GlassesConfidence.POSSIBLE, meta.confidence)
        assertTrue(meta.reason.contains("VR"))
        // 안경 회사 코드가 같이 있으면 확실한 쪽을 쓴다.
        assertEquals(GlassesConfidence.STRONG, ble(ids = setOf(0x01AB, 0x0D53))!!.confidence)
    }

    @Test fun serviceCodeAppearanceAndProductNamesCount() {
        assertEquals(GlassesConfidence.STRONG, ble(hints = setOf(SignalHint.META_SERVICE))!!.confidence)
        assertEquals(GlassesConfidence.STRONG, ble(hints = setOf(SignalHint.GLASSES))!!.confidence)
        listOf("Ray-Ban Meta 0A1B", "RayNeo X3 Pro", "Rokid Glasses", "Galaxy Glasses", "Even G1_57_L_A1B2C3", "XREAL One", "AI Glasses-7F21", "홍길동의 AI 안경")
            .forEach { name -> assertNotNull(name, ble(name = name)) }
    }

    @Test fun everydayDevicesAreNotGlasses() {
        listOf("Galaxy Buds2 Pro", "AirPods Pro", "Galaxy Watch6", "Class TV", "Meta", "Glassdoor", "glasses", "BLE 기기 EE:FF")
            .forEach { name -> assertNull(name, ble(name = name)) }
        assertNull(ble(ids = setOf(0x004C, 0x0075))) // Apple, Samsung: 휴대폰과 같은 코드라 근거로 쓰지 않는다.
        // 와이파이 이름은 안경 근거로 쓰지 않는다.
        assertNull(glassesEvidence(ScanSource.WIFI, "AA:BB:CC:DD:EE:FF", "Ray-Ban Meta", emptySet(), emptySet()))
    }

    @Test fun metaServiceCodeIsReadFromTheAdvertisement() {
        fun ad(type: Int, vararg data: Int) = byteArrayOf((data.size + 1).toByte(), type.toByte()) + data.map { it.toByte() }
        assertTrue(SignalHint.META_SERVICE in bleHints(ad(0x03, 0x5F, 0xFD)))
        assertTrue(SignalHint.META_SERVICE in bleHints(ad(0x16, 0x5F, 0xFD, 0x01, 0x02)))
        assertFalse(SignalHint.META_SERVICE in bleHints(ad(0x03, 0x2C, 0xFE)))
    }
}
