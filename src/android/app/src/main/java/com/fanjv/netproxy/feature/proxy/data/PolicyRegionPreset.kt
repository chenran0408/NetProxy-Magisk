package com.fanjv.netproxy.feature.proxy.data

internal enum class PolicyRegionPreset(aliases: String) {
    HK("🇭🇰|香港|港区|港區|hong[ _-]?kong"),
    MO("🇲🇴|澳门|澳門|macau|macao"),
    TW("🇹🇼|台湾|臺灣|台灣|台北|taiwan"),
    JP("🇯🇵|日本|东京|東京|大阪|japan"),
    KR("🇰🇷|韩国|韓國|首尔|首爾|korea"),
    SG("🇸🇬|新加坡|狮城|獅城|singapore"),
    US("🇺🇸|美国|美國|洛杉矶|洛杉磯|united[ _-]?states"),
    CA("🇨🇦|加拿大|canada"),
    GB("🇬🇧|英国|英國|伦敦|倫敦|united[ _-]?kingdom"),
    DE("🇩🇪|德国|德國|germany"),
    FR("🇫🇷|法国|法國|france"),
    NL("🇳🇱|荷兰|荷蘭|netherlands"),
    AU("🇦🇺|澳大利亚|澳大利亞|australia"),
    IN("🇮🇳|印度|india"),
    TR("🇹🇷|土耳其|turkey"),
    RU("🇷🇺|俄罗斯|俄羅斯|russia");

    private val code = when (name) {
        "GB" -> "(?:GB|UK)"
        "US" -> "(?:US|USA)"
        else -> name
    }

    // Match the node portion of the core's Provider/node tag.
    val include = "(?i)^[^/]+/(?:.*(?:$aliases)|(?:.*[^a-z])?$code(?:[^a-z]|$))"
    val tag = "$name Auto"

    fun applyTo(draft: PolicyGroupDraft, blockTag: String): PolicyGroupDraft = draft.copy(
        tag = draft.tag.takeUnless { it.isBlank() || it == draft.regionPreset?.tag } ?: tag,
        type = "urltest",
        outbounds = listOf(blockTag),
        useAll = draft.useAll || draft.providers.isEmpty(),
        defaultMember = "",
        include = include,
        addAuto = false,
    )
}

internal val PolicyGroupDraft.regionPreset: PolicyRegionPreset?
    get() = PolicyRegionPreset.entries.firstOrNull { type == "urltest" && include == it.include }
