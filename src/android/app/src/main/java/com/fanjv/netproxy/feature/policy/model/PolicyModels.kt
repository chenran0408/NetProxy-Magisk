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
    val ports: List<Int> = emptyList(),
    @SerialName("group_id") val groupId: String = "default",
    @SerialName("node_tag") val nodeTag: String = "",
    val mode: String = "auto",
    @SerialName("runtime_tag") val runtimeTag: String = "Proxy",
    @SerialName("chain_available") val chainAvailable: Boolean = false,
    val groups: List<CatalogNodeGroup> = emptyList(),
    val persisted: Boolean = false,
    @SerialName("runtime_synced") val runtimeSynced: Boolean = false,
)

@Serializable
internal data class PolicyResponse(val schema: Int = 1, val policies: List<PolicyModel> = emptyList())
