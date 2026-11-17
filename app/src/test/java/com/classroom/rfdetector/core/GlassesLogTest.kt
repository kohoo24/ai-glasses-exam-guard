package com.classroom.rfdetector.core

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlassesLogTest {
    @get:Rule val directory = TemporaryFolder()

    private fun entry(at: Long, background: Boolean = false) = GlassesLogEntry("EE:00:00:00:00:01", GlassesConfidence.STRONG,
        "Luxottica · Ray-Ban Meta·Oakley Meta", "안경 회사 코드 0x0D53", at, at + 8_000, -58, 12, acknowledged = false, seenInBackground = background)

    @Test fun recordsSurviveReopeningAndTheSameSessionIsUpdatedInPlace() {
        val store = GlassesLogStore(directory.root)
        store.upsert(GlassesLogSession("a", 1_000, null, quick = true, entries = emptyList()))
        store.upsert(GlassesLogSession("a", 1_000, 90_000, quick = true, entries = listOf(entry(5_000, background = true))))
        store.upsert(GlassesLogSession("b", 200_000, null, quick = false, entries = emptyList()))
        val loaded = GlassesLogStore(directory.root).load()
        assertEquals(listOf("b", "a"), loaded.map { it.id })
        val a = loaded.last()
        assertEquals(90_000L, a.endedAtMs)
        assertEquals(entry(5_000, background = true), a.entries.single())
    }

    @Test fun onlyTheMostRecentSessionsAreKept() {
        val store = GlassesLogStore(directory.root, keep = 3)
        (1..5).forEach { store.upsert(GlassesLogSession("s$it", it * 1_000L, null, true, emptyList())) }
        assertEquals(listOf("s5", "s4", "s3"), store.load().map { it.id })
        store.clear()
        assertTrue(store.load().isEmpty())
    }

    @Test fun sharedTextExplainsTheEvidenceButLeavesOutAddressesAndNames() {
        val text = GlassesLogSession("a", 1_700_000_000_000, null, quick = true, entries = listOf(entry(1_700_000_100_000, background = true))).shareText()
        assertTrue(text.contains("AI 안경 신호 · Luxottica"))
        assertTrue(text.contains("근거: 안경 회사 코드 0x0D53"))
        assertTrue(text.contains("화면 꺼짐 중 수신"))
        assertTrue(text.contains("끝나지 않음"))
        assertTrue(text.contains("증거가 아닙니다"))
        assertFalse(text.contains("EE:00"))
        assertTrue(GlassesLogSession("b", 1_700_000_000_000, 1_700_000_600_000, false, emptyList()).shareText().contains("잡힌 AI 안경 신호: 없음"))
    }

    @Test fun screenOffFiltersCoverEveryGlassesCompanyAndTheMetaService() {
        val filters = com.classroom.rfdetector.scanner.GlassesWatchService.glassesFilters()
        assertEquals(GLASSES_COMPANY_IDS.keys, filters.mapNotNull { it.manufacturerId.takeIf { id -> id >= 0 } }.toSet())
        assertTrue(filters.any { it.serviceUuid?.uuid.toString().startsWith("0000fd5f") })
    }
}
