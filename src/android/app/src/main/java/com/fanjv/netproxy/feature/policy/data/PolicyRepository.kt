package com.fanjv.netproxy.feature.policy.data

import com.fanjv.netproxy.core.api.ModuleApiClient
import com.fanjv.netproxy.feature.policy.model.ChainProxyModel
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import com.fanjv.netproxy.feature.policy.model.PolicyResponse
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.map

internal class PolicyRepository(private val client: ModuleApiClient) {
    suspend fun list(): List<PolicyModel> = client.read<PolicyResponse>("/policies").policies

    suspend fun chain(): ChainProxyModel = client.read<ChainProxyModel>("/chain")

    fun observeGroups() = client.observe(setOf("groups")).map { result -> result.map { Unit } }

    suspend fun save(policy: PolicyModel) {
        client.put("/policies/${policy.id}", buildJsonObject {
            put("group_id", policy.groupId)
            put("node_tag", policy.nodeTag)
            put("mode", policy.mode)
            put("region", policy.region)
        })
    }

    suspend fun test(policy: PolicyModel): NodeDelayResult = client.json.decodeFromJsonElement<NodeDelayResult>(
        client.post("/policies/${policy.id}/url-test").data
    )

    suspend fun saveChain(front: String, landing: String): ChainProxyModel =
        client.put("/chain", buildJsonObject {
            put("front", front)
            put("landing", landing)
        }).data.let { client.json.decodeFromJsonElement<ChainProxyModel>(it) }
}
