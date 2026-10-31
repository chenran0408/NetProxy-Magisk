package com.fanjv.netproxy.feature.policy.data

import com.fanjv.netproxy.core.api.ModuleApiClient
import com.fanjv.netproxy.core.api.TestApiTransport
import com.fanjv.netproxy.core.api.apiSuccess
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyRepositoryTest {
    @Test fun listDecodesPolicyGroupsAndSaveUsesStableContract() = runBlocking {
        val calls = mutableListOf<String>()
        val client = ModuleApiClient(TestApiTransport { request ->
            calls += "${request.method} ${request.path}"
            when (request.method to request.path) {
                "GET" to "/policies" -> apiSuccess("""
                    {"schema":1,"policies":[{"id":"ai","name":"AI","group_id":"default","mode":"auto","groups":[{"group":{"id":"default","name":"Local","type":"local"},"nodes":[{"tag":"Tokyo"}]}]}]}
                """.trimIndent())
                "PUT" to "/policies/ai" -> {
                    assertEquals("default", request.text("group_id"))
                    assertEquals("Tokyo", request.text("node_tag"))
                    assertEquals("manual", request.text("mode"))
                    apiSuccess("""{"policy":{"id":"ai"},"persisted":true,"runtime_synced":false}""")
                }
                else -> error("Unexpected policy request: $request")
            }
        })
        val repository = PolicyRepository(client)
        val policy = repository.list().single()
        assertEquals("ai", policy.id)
        assertEquals("Tokyo", policy.groups.single().nodes.single().tag)
        repository.save(policy.copy(groupId = "default", nodeTag = "Tokyo", mode = "manual"))
        assertEquals(listOf("GET /policies", "PUT /policies/ai"), calls)
    }

    @Test fun urlTestDecodesDelayGroups() = runBlocking {
        val client = ModuleApiClient(TestApiTransport { request ->
            assertEquals("POST", request.method)
            assertEquals("/policies/ai/url-test", request.path)
            apiSuccess("""{"target":"auto","groups":[{"tag":"default","items":[{"tag":"Tokyo","url_test_delay":70}]}]}""")
        })
        val result = PolicyRepository(client).test(PolicyModel("ai", "AI"))
        assertEquals("auto", result.target)
        assertEquals(70, result.groups.single().items.single().urlTestDelay)
        assertTrue(result.groups.single().items.single().urlTestTime == null)
    }

    @Test fun regionAutoSavesAnEmptyNodeTagAndStringRegion() = runBlocking {
        val client = ModuleApiClient(TestApiTransport { request ->
            assertEquals("PUT", request.method)
            assertEquals("/policies/ai", request.path)
            assertEquals("default", request.text("group_id"))
            assertEquals("", request.text("node_tag"))
            assertEquals("auto", request.text("mode"))
            assertEquals("us", request.text("region"))
            apiSuccess("""{"policy":{"id":"ai"},"persisted":true,"runtime_synced":false}""")
        })

        PolicyRepository(client).save(PolicyModel("ai", "AI", groupId = "default", mode = "auto", region = "us"))
    }
}
