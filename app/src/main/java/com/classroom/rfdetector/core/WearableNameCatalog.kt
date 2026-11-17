package com.classroom.rfdetector.core

import java.text.Normalizer

/** A recognizable advertised name is a candidate, not proof of hardware or AI use. */
enum class WearableKind {
    SMART_GLASSES, SMARTWATCH, CAMERA,
}

data class WearableNameMatch(
    val brand: String,
    val kind: WearableKind,
)

object WearableNameCatalog {
    private data class Rule(val brand: String, val kind: WearableKind, val expression: String) {
        val pattern = Regex("(?:(?<![\\p{L}\\p{N}])|(?<=의))(?:$expression)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
    }

    private val rules = listOf(
        Rule("Ray-Ban / Meta", WearableKind.SMART_GLASSES,
            "Meta\\s+Ray[- ]?Ban\\s+Display|Ray[- ]?Ban\\s+Meta(?:\\s+(?:Audio|Wayfarer|Headliner|Skyler))?(?:\\s*\\(?Gen\\s*\\d+\\)?)?|(?:레이밴|레이뱅)\\s*메타(?:\\s*\\d+세대)?"),
        Rule("Oakley / Meta", WearableKind.SMART_GLASSES,
            "Oakley\\s+Meta(?:\\s+(?:HSTN|Vanguard))?|오클리\\s*메타(?:\\s*(?:HSTN|뱅가드))?"),
        Rule("Ray-Ban / Meta", WearableKind.SMART_GLASSES, "Ray[- ]?Ban\\s+Stories"),
        Rule("Samsung", WearableKind.SMART_GLASSES, "Galaxy\\s+Glass(?:es)?|갤럭시\\s*(?:글래스|글라스)"),
        Rule("Snap", WearableKind.SMART_GLASSES, "Spectacles"),
        Rule("Rokid", WearableKind.SMART_GLASSES, "Rokid(?:\\s*(?:Glasses|AI|Max|Air|Station))?|로키드"),
        Rule("RayNeo", WearableKind.SMART_GLASSES, "RayNeo(?:\\s*(?:X\\d|Air\\s*\\d*|V\\d))?|레이네오"),
        Rule("XREAL", WearableKind.SMART_GLASSES, "XREAL(?:\\s*(?:One|Air|Beam)(?:\\s*\\d)?)?|Nreal(?:\\s*Air)?"),
        Rule("INMO", WearableKind.SMART_GLASSES, "INMO(?:\\s*(?:Air|Go)\\s*\\d*)?"),
        Rule("Even Realities", WearableKind.SMART_GLASSES, "Even\\s*G\\d"),
        Rule("Vuzix", WearableKind.SMART_GLASSES, "Vuzix(?:\\s*Z\\d+)?"),
        Rule("Halliday", WearableKind.SMART_GLASSES, "Halliday\\s*(?:Glasses|AI)"),
        Rule("제조사 모름", WearableKind.SMART_GLASSES,
            "(?:AI|Smart)[\\s_-]*Glass(?:es)?|(?:AI|스마트)\\s*(?:안경|글래스|글라스)"),
        Rule("Apple", WearableKind.SMARTWATCH,
            "Apple\\s+Watch(?:\\s+(?:Series\\s*\\d+|Ultra(?:\\s*\\d+)?|SE(?:\\s*\\d+)?))?|애플\\s*워치(?:\\s*(?:시리즈\\s*\\d+|울트라(?:\\s*\\d+)?|SE(?:\\s*\\d+)?|\\d+))?"),
        Rule("Samsung", WearableKind.SMARTWATCH,
            "Galaxy\\s+Watch(?:\\s*(?:Ultra(?:\\s*\\d+)?|\\d+(?:\\s*(?:Classic|Pro))?|FE))?|갤럭시\\s*워치(?:\\s*(?:울트라(?:\\s*\\d+)?|\\d+(?:\\s*(?:클래식|프로))?|FE))?"),
        Rule("GoPro", WearableKind.CAMERA,
            "GoPro\\s+HERO\\s*\\d+(?:\\s+Black)?|고프로\\s*히어로\\s*\\d+(?:\\s*블랙)?"),
    )

    /** 갱신마다 같은 이름을 다시 검사하지 않게 결과를 기억한다. 없음은 NONE으로 기억한다. */
    private val NONE = WearableNameMatch("", WearableKind.CAMERA)
    private val cache = java.util.concurrent.ConcurrentHashMap<String, WearableNameMatch>()

    fun match(name: String?): WearableNameMatch? {
        if (name.isNullOrBlank()) return null
        if (cache.size > 5_000) cache.clear()
        return cache.getOrPut(name) { find(name) ?: NONE }.takeUnless { it === NONE }
    }

    private fun find(name: String): WearableNameMatch? {
        val normalized = Normalizer.normalize(name, Normalizer.Form.NFKC)
        return rules.firstNotNullOfOrNull { rule ->
            rule.pattern.find(normalized)?.let { WearableNameMatch(rule.brand, rule.kind) }
        }
    }
}
