package com.fanjv.netproxy.feature.policy.model

import com.fanjv.netproxy.feature.catalog.model.CatalogNodeGroup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PolicyModel(
    val id: String,
    val name: String,
    val icon: String = "",
    val type: String = "domain",
    @SerialName("rule_sets") val ruleSets: List<String> = emptyList(),
    @SerialName("package_names") val packageNames: List<String> = emptyList(),
    @SerialName("domain_suffixes") val domainSuffixes: List<String> = emptyList(),
    val ports: List<Int> = emptyList(),
    @SerialName("group_id") val groupId: String = "default",
    @SerialName("node_tag") val nodeTag: String = "",
    val mode: String = "auto",
    val region: String = "",
    @SerialName("runtime_tag") val runtimeTag: String = "Proxy",
    val disabled: Boolean = false,
    @SerialName("chain_available") val chainAvailable: Boolean = false,
    val groups: List<CatalogNodeGroup> = emptyList(),
    val persisted: Boolean = false,
    @SerialName("runtime_synced") val runtimeSynced: Boolean = false,
) {
    val isCustom: Boolean get() = id.startsWith("custom-")
}
@Serializable
internal data class PolicyResponse(val schema: Int = 1, val policies: List<PolicyModel> = emptyList())

@Serializable
internal data class PolicySaveResponse(
    val policy: PolicyModel,
    val persisted: Boolean,
    @SerialName("runtime_synced") val runtimeSynced: Boolean,
) {
    fun savedPolicy() = policy.copy(persisted = persisted, runtimeSynced = runtimeSynced)
}

@Serializable
internal data class ChainProxyModel(
    val available: Boolean = false,
    val running: Boolean = false,
    @SerialName("landing_port") val landingPort: Int = 0,
    val front: ChainSelector = ChainSelector(),
    val landing: ChainSelector = ChainSelector(),
)

@Serializable
internal data class ChainSelector(
    val tag: String = "",
    val selected: String = "",
    val items: List<ChainNode> = emptyList(),
)

@Serializable
internal data class ChainNode(
    val tag: String = "",
    val type: String = "",
    @SerialName("url_test_time") val urlTestTime: Long? = null,
    @SerialName("url_test_delay") val urlTestDelay: Int? = null,
)
