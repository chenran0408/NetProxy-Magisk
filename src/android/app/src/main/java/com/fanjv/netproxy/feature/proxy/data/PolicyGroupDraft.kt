package com.fanjv.netproxy.feature.proxy.data

import com.fanjv.netproxy.feature.routing.model.LocalRuleDocument
import com.fanjv.netproxy.feature.routing.model.RuleField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal val editablePolicyTypes = setOf("selector", "urltest", "loadbalance")
internal const val AUTO_POLICY_TAG = "Auto"

private val builtInPolicyTags = setOf(
    "Proxy", AUTO_POLICY_TAG, "AI", "GitHub", "YouTube", "TikTok", "Google FCM",
    "Google", "Telegram", "Netflix", "Spotify", "Bilibili",
)

internal data class PolicyGroupDraft(
    val tag: String = "",
    val type: String = "selector",
    val outbounds: List<String> = emptyList(),
    val providers: List<String> = emptyList(),
    val useAll: Boolean = false,
    val defaultMember: String = "",
    val include: String = "",
    val exclude: String = "",
    val testUrl: String = "",
    val testInterval: String = "",
    val addAuto: Boolean = false,
    val appPackages: String = "",
) {
    val hasMembers: Boolean get() = outbounds.isNotEmpty() || providers.isNotEmpty() || useAll || addAuto

    companion object {
        fun from(group: JsonObject) = PolicyGroupDraft(
            tag = group.text("tag"),
            type = group.text("type"),
            outbounds = group.names("outbounds"),
            providers = group.names("providers"),
            useAll = (group["use_all_providers"] as? JsonPrimitive)?.booleanOrNull == true,
            defaultMember = group.text("default"),
            include = group.text("include"),
            exclude = group.text("exclude"),
            testUrl = group.text("url"),
            testInterval = group.text("interval"),
        )
    }
}

internal data class PolicyGroupDocument(
    val root: JsonObject,
    val revision: String,
    val originalTag: String?,
    val memberTags: List<String>,
    val providerChoices: List<String>,
) {
    private val outbounds = root.policySection("outbounds")
    private val route = LocalRuleDocument(
        JsonObject(mapOf("route" to (root["route"]?.takeUnless { it == JsonNull } ?: JsonObject(emptyMap())))), nativeRoute = true
    )
    val canEditName = originalTag !in builtInPolicyTags
    val canEditPackages = canEditName && route.policyStart != null

    private val index = originalTag?.let { tag ->
        outbounds.indexOfFirst { it.text("tag") == tag }.also { require(it >= 0) }
    }
    private val original = index?.let(outbounds::get)
    val initial = original?.let(PolicyGroupDraft::from)?.copy(
        appPackages = route.policyPackages(requireNotNull(originalTag)).joinToString("\n")
    ) ?: PolicyGroupDraft()
    val blockTag = outbounds.firstOrNull { it.text("type") == "block" }?.text("tag")
    val typeChoices = when (initial.type) {
        "selector", "urltest" -> listOf("selector", "urltest")
        "loadbalance" -> listOf("selector", "urltest", "loadbalance")
        else -> emptyList()
    }
    val canAddAuto: Boolean get() = originalTag == "Proxy" && initial.type == "selector" &&
        (initial.outbounds.isNotEmpty() || initial.defaultMember.isNotBlank()) &&
        AUTO_POLICY_TAG !in memberTags && AUTO_POLICY_TAG !in initial.outbounds &&
        outbounds.any { it.text("tag") == "block" && it.text("type") == "block" }

    fun savedName(name: String): String = if (name == originalTag) name else name.trim()

    fun nameTaken(name: String): Boolean = savedName(name).let { it != originalTag && it in memberTags }

    fun valid(draft: PolicyGroupDraft): Boolean {
        val name = savedName(draft.tag)
        return name.isNotEmpty() && !nameTaken(name) && (canEditName || name == originalTag) &&
            draft.hasMembers && draft.outbounds.none { it == name || it == originalTag } &&
            draft.type in typeChoices && (!draft.addAuto || (draft.type == "selector" && canAddAuto)) &&
            (draft.appPackages == initial.appPackages || (canEditPackages &&
                (draft.appPackages.isBlank() || RuleField.PackageName.inputError(draft.appPackages) == null)))
    }

    fun updated(draft: PolicyGroupDraft): String {
        require(valid(draft))
        val tag = savedName(draft.tag)
        val fields = original.orEmpty().toMutableMap()
        fields["tag"] = JsonPrimitive(tag)
        fields["type"] = JsonPrimitive(draft.type)
        if (draft.type != initial.type) {
            // Remove only options known to be incompatible with the target core type.
            when (draft.type) {
                "selector" -> listOf("url", "interval", "idle_timeout", "tolerance", "fallback", "ttl", "strategy")
                "urltest" -> listOf("default", "ttl", "strategy")
                else -> emptyList()
            }.forEach(fields::remove)
        }
        fun updateText(key: String, value: String, previous: String) {
            if (value != previous) {
                if (value.isBlank()) fields.remove(key) else fields[key] = JsonPrimitive(value)
            }
        }
        fun updateNames(key: String, value: List<String>, previous: List<String>) {
            if (value != previous) {
                if (value.isEmpty()) fields.remove(key) else fields[key] = JsonArray(value.map(::JsonPrimitive))
            }
        }
        val members = if (draft.addAuto) (draft.outbounds + AUTO_POLICY_TAG).distinct() else draft.outbounds
        updateNames("outbounds", members, initial.outbounds)
        updateNames("providers", draft.providers, initial.providers)
        if (draft.useAll != initial.useAll) {
            if (draft.useAll) fields["use_all_providers"] = JsonPrimitive(true) else fields.remove("use_all_providers")
        }
        if (draft.type == "selector") updateText("default", draft.defaultMember, initial.defaultMember)
        if (draft.type == "urltest") {
            updateText("url", draft.testUrl, initial.testUrl)
            updateText("interval", draft.testInterval, initial.testInterval)
        }
        updateText("include", draft.include, initial.include)
        updateText("exclude", draft.exclude, initial.exclude)
        val updated = outbounds.toMutableList()
        if (index == null) updated.add(JsonObject(fields)) else updated[index] = JsonObject(fields)
        if (draft.addAuto) updated.add(JsonObject(mapOf(
            "type" to JsonPrimitive("urltest"),
            "tag" to JsonPrimitive(AUTO_POLICY_TAG),
            "outbounds" to JsonArray(listOf(JsonPrimitive("block"))),
            "use_all_providers" to JsonPrimitive(true),
        )))
        val fieldsWithGroup = root + ("outbounds" to JsonArray(updated))
        val packageNames = RuleField.PackageName.values(draft.appPackages)
        if (packageNames == RuleField.PackageName.values(initial.appPackages)) {
            return JsonObject(fieldsWithGroup).toString()
        }
        // The module rewrites old outbound references when the group name changes.
        val routed = route.withPolicyPackages(originalTag ?: tag, packageNames)
        return JsonObject(fieldsWithGroup + ("route" to routed.root.getValue("route"))).toString()
    }
}

internal fun parsePolicySection(content: String, key: String): List<JsonObject> =
    Json.parseToJsonElement(content).jsonObject.policySection(key)

internal fun JsonObject.policySection(key: String): List<JsonObject> =
    when (val value = get(key)) {
        null, JsonNull -> emptyList()
        else -> value.jsonArray.map(JsonElement::jsonObject)
    }

internal fun JsonObject.text(key: String): String = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

private fun JsonObject.names(key: String): List<String> = when (val field = get(key)) {
    is JsonArray -> field.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    is JsonPrimitive -> listOfNotNull(field.contentOrNull)
    else -> emptyList()
}
