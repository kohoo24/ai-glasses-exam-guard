package com.classroom.rfdetector.core

/**
 * AI 안경으로 볼 근거. 모두 기기가 스스로 알린 값이라 흉내 낼 수 있고, 근거가 없다고 안경이 없는 것도 아니다.
 *
 * 근거 출처(2026-11 확인):
 * - 회사 코드: Bluetooth SIG 회사 식별자 등록부(앱에 포함). Luxottica·Meta·Snap 코드는 공개된 안경 탐지 앱
 *   Nearby Glasses, NoPeek이 실제 Ray-Ban Meta·Oakley Meta·Spectacles 광고에서 확인해 쓰는 값이다.
 * - 0xFD5F: Meta(Oculus)에 등록된 16비트 서비스 코드. Ray-Ban Meta 광고에서 보고된 값이다.
 * - 이름: 제조사 공식 제품명. 이름은 바꿀 수 있다.
 *
 * Ray-Ban Meta 2세대는 휴대폰과 연결된 뒤 광고를 멈춘다고 보고되었다. 그래서 켜는 순간, 다시 연결하는 순간처럼
 * 짧게만 잡히는 경우가 많고, 잡힌 기록을 사라진 뒤에도 남겨 두어야 한다.
 */
enum class GlassesConfidence(val label: String) {
    /** 안경 회사 전용 코드, 안경 종류 값, 안경 제품명 */
    STRONG("AI 안경 신호"),
    /** 안경 말고 다른 제품(VR 헤드셋 등)도 쓰는 회사 코드 */
    POSSIBLE("AI 안경일 수 있는 신호"),
}

data class GlassesEvidence(val confidence: GlassesConfidence, val brand: String, val reason: String)

/** 회사 코드 → (제조사 설명, 확실도). 삼성·구글·샤오미처럼 휴대폰과 같은 코드를 쓰는 회사는 넣지 않는다. */
val GLASSES_COMPANY_IDS: Map<Int, Pair<String, GlassesConfidence>> = mapOf(
    0x0D53 to ("Luxottica · Ray-Ban Meta·Oakley Meta" to GlassesConfidence.STRONG),
    0x03C2 to ("Snap · Spectacles" to GlassesConfidence.STRONG),
    0x10F9 to ("Even Realities · 디스플레이 안경" to GlassesConfidence.STRONG),
    0x060C to ("Vuzix · 스마트 안경" to GlassesConfidence.STRONG),
    0x058E to ("Meta Platforms Technologies · 안경 또는 Quest" to GlassesConfidence.POSSIBLE),
    0x01AB to ("Meta Platforms · 안경 또는 Quest" to GlassesConfidence.POSSIBLE),
)

/** Meta(Oculus)에 등록된 서비스 코드. Ray-Ban Meta 광고에서 보고되었다. */
const val META_GLASSES_SERVICE = 0xFD5F

/** 블루투스로만 판단한다. 와이파이 이름은 안경 근거로 쓰지 않는다. */
fun glassesEvidence(source: ScanSource, address: String, name: String, manufacturerIds: Set<Int>, hints: Set<SignalHint>): GlassesEvidence? {
    if (source != ScanSource.BLE) return null
    val byId = manufacturerIds.mapNotNull { id -> GLASSES_COMPANY_IDS[id]?.let { id to it } }.minByOrNull { it.second.second.ordinal }
    val named = WearableNameCatalog.match(advertisedName(address, name))?.takeIf { it.kind == WearableKind.SMART_GLASSES }
    return when {
        named != null -> GlassesEvidence(GlassesConfidence.STRONG, named.brand, "기기가 알린 이름이 안경 제품명과 같음")
        byId != null && byId.second.second == GlassesConfidence.STRONG ->
            GlassesEvidence(GlassesConfidence.STRONG, byId.second.first, "안경 회사 코드 0x%04X".format(byId.first))
        SignalHint.GLASSES in hints -> GlassesEvidence(GlassesConfidence.STRONG, "제조사 모름", "기기가 스스로 '안경' 종류라고 알림")
        SignalHint.META_SERVICE in hints -> GlassesEvidence(GlassesConfidence.STRONG, "Meta 계열", "Meta 안경 서비스 코드 0xFD5F")
        byId != null -> GlassesEvidence(GlassesConfidence.POSSIBLE, byId.second.first, "Meta 회사 코드 0x%04X · VR 헤드셋도 같은 코드를 씀".format(byId.first))
        else -> null
    }
}

val TrackedDevice.glasses: GlassesEvidence? get() = glassesEvidence(source, key, displayName, manufacturerIds, hints)
val SnapshotSignal.glasses: GlassesEvidence? get() = glassesEvidence(source, address, name, manufacturerIds, hints)

/** 휴대폰으로 사진·영상을 넘기는 데 쓰일 수 있는 와이파이: 와이파이 다이렉트, 휴대폰 핫스팟 기본 이름. */
val TrackedDevice.transferWifi: Boolean get() = source == ScanSource.WIFI && (SignalHint.WIFI_DIRECT in hints || SignalHint.HOTSPOT in hints)
