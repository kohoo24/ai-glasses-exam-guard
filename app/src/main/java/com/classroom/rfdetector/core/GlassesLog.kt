package com.classroom.rfdetector.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 감시 한 번 동안 잡힌 AI 안경 신호. 앱이 꺼져도 남아, 시험이 끝난 뒤 사실 확인에 쓴다.
 * 기기가 알린 이름은 개인 이름이 들어 있을 수 있어 저장하지 않는다.
 */
data class GlassesLogEntry(
    val address: String,
    val confidence: GlassesConfidence,
    val brand: String,
    val reason: String,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val peakRssi: Int?,
    val observations: Int,
    val acknowledged: Boolean,
    /** 화면이 꺼진 동안 받은 신호가 있었음 */
    val seenInBackground: Boolean = false,
)

data class GlassesLogSession(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long?,
    /** true: AI 안경 바로 탐지, false: 빈 교실 비교 감시 */
    val quick: Boolean,
    val entries: List<GlassesLogEntry>,
)

fun GlassesSighting.toLogEntry(background: Boolean = false) = GlassesLogEntry(
    device.key, evidence.confidence, evidence.brand, evidence.reason, device.firstSeenMs, device.lastSeenMs,
    device.peakRssi, device.observationCount, acknowledged, background,
)

/** 최근 감시 기록을 휴대폰 안에만 보관한다. 오래된 것부터 지운다. 기기 백업에서는 제외된다. */
class GlassesLogStore(directory: File, private val keep: Int = 30) {
    private val dir = directory
    private val file = File(directory, "glasses-log.json")

    fun load(): List<GlassesLogSession> {
        if (!file.exists()) return emptyList()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.getInt("version") == 1) { "지원하지 않는 기록 파일 버전입니다." }
        val sessions = root.getJSONArray("sessions")
        return (0 until sessions.length()).map { decode(sessions.getJSONObject(it)) }
    }

    /** 같은 id의 감시는 덮어쓰고, 새 감시는 맨 앞에 넣는다. */
    fun upsert(session: GlassesLogSession): List<GlassesLogSession> {
        val all = (listOf(session) + runCatching { load() }.getOrDefault(emptyList()).filter { it.id != session.id })
            .sortedByDescending { it.startedAtMs }.take(keep)
        write(all)
        return all
    }

    fun clear() { write(emptyList()) }

    private fun write(sessions: List<GlassesLogSession>) {
        val root = JSONObject().put("version", 1).put("sessions", JSONArray().apply { sessions.forEach { put(encode(it)) } })
        val temporary = File.createTempFile("glasses-log-", ".tmp", dir)
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(root.toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun encode(session: GlassesLogSession) = JSONObject().apply {
        put("id", session.id); put("startedAtMs", session.startedAtMs); put("endedAtMs", session.endedAtMs ?: JSONObject.NULL)
        put("quick", session.quick)
        put("entries", JSONArray().apply { session.entries.forEach { e ->
            put(JSONObject().apply {
                put("address", e.address); put("confidence", e.confidence.name); put("brand", e.brand); put("reason", e.reason)
                put("firstSeenMs", e.firstSeenMs); put("lastSeenMs", e.lastSeenMs); put("peakRssi", e.peakRssi ?: JSONObject.NULL)
                put("observations", e.observations); put("acknowledged", e.acknowledged); put("background", e.seenInBackground)
            })
        } })
    }

    private fun decode(json: JSONObject): GlassesLogSession {
        val rows = json.getJSONArray("entries")
        val entries = (0 until rows.length()).mapNotNull { index -> rows.getJSONObject(index).let { row ->
            val confidence = GlassesConfidence.entries.firstOrNull { it.name == row.getString("confidence") } ?: return@mapNotNull null
            GlassesLogEntry(row.getString("address"), confidence, row.getString("brand"), row.getString("reason"),
                row.getLong("firstSeenMs"), row.getLong("lastSeenMs"), if (row.isNull("peakRssi")) null else row.getInt("peakRssi"),
                row.getInt("observations"), row.getBoolean("acknowledged"), row.optBoolean("background", false))
        } }
        return GlassesLogSession(json.getString("id"), json.getLong("startedAtMs"),
            if (json.isNull("endedAtMs")) null else json.getLong("endedAtMs"), json.getBoolean("quick"), entries)
    }
}

/** 감독관이 보고서나 메신저로 넘길 수 있는 글. 기기 이름과 주소는 넣지 않는다. */
fun GlassesLogSession.shareText(): String {
    val day = SimpleDateFormat("yyyy년 M월 d일 HH:mm", Locale.KOREAN)
    val time = SimpleDateFormat("HH:mm:ss", Locale.KOREAN)
    return buildString {
        appendLine("AI 안경 탐지 · AI 안경 신호 기록")
        appendLine("감시: ${if (quick) "AI 안경 바로 탐지" else "빈 교실 비교 감시"}")
        appendLine("시작 ${day.format(Date(startedAtMs))}" + (endedAtMs?.let { " · 끝 ${time.format(Date(it))}" } ?: " · 끝나지 않음(앱이 닫힘)"))
        if (entries.isEmpty()) appendLine("잡힌 AI 안경 신호: 없음")
        else {
            appendLine("잡힌 AI 안경 신호: ${entries.size}건")
            entries.sortedBy { it.firstSeenMs }.forEachIndexed { index, e ->
                append("${index + 1}. ${e.confidence.label} · ${e.brand}")
                append(" · 처음 ${time.format(Date(e.firstSeenMs))} · 마지막 ${time.format(Date(e.lastSeenMs))}")
                e.peakRssi?.let { append(" · 가장 셀 때 $it dBm(${RssiFilter.classify(RssiFilter.distanceM(it.toDouble())).label})") }
                append(" · ${e.observations}회 수신")
                if (e.seenInBackground) append(" · 화면 꺼짐 중 수신")
                if (e.acknowledged) append(" · 감독관 확인함")
                appendLine()
                appendLine("   근거: ${e.reason}")
            }
        }
        appendLine()
        append("블루투스 신호는 흉내 낼 수 있고 벽 너머의 기기도 잡힙니다. 이 기록은 확인이 필요했다는 표시일 뿐 부정행위의 증거가 아닙니다. ")
        append("신호가 없었다고 안경이 없었던 것도 아닙니다.")
    }
}
