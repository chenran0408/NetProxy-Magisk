package com.fanjv.netproxy.feature.policy.presentation

import com.fanjv.netproxy.core.api.ModuleApiClient
import com.fanjv.netproxy.core.api.ModuleApiException
import com.fanjv.netproxy.core.api.TestApiTransport
import com.fanjv.netproxy.core.api.apiSuccess
import com.fanjv.netproxy.feature.policy.data.PolicyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PolicyViewModelTest {
    @Before fun setMainDispatcher() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun resetMainDispatcher() { Dispatchers.resetMain() }

    @Test fun loadsPolicyRuntimeStateAndSavesManualSelection() = runBlocking {
        var selectedNode = ""
        val repository = PolicyRepository(ModuleApiClient(TestApiTransport { request ->
            when (request.method to request.path) {
                "GET" to "/policies" -> apiSuccess("""{"policies":[{"id":"ai","name":"AI","group_id":"default","mode":"auto","runtime_synced":true,"groups":[]}]}""")
                "PUT" to "/policies/ai" -> {
                    selectedNode = request.text("node_tag")
                    apiSuccess("""{"persisted":true,"runtime_synced":true}""")
                }
                else -> error("Unexpected request: $request")
            }
        }))
        val viewModel = PolicyViewModel(repository)

        viewModel.refresh()
        withTimeout(5_000) { viewModel.state.first { !it.loading } }
        assertTrue(viewModel.state.value.policies.single().runtimeSynced)
        viewModel.select(viewModel.state.value.policies.single(), "default", "Tokyo")
        withTimeout(5_000) { viewModel.state.first { it.busyId.isEmpty() } }
        assertEquals("Tokyo", selectedNode)
        assertTrue(viewModel.state.value.error.isEmpty())
    }

    @Test fun refreshAndMutationFailuresRemainVisible() = runBlocking {
        var failRead = true
        val repository = PolicyRepository(ModuleApiClient(TestApiTransport { request ->
            if (request.method == "GET" && failRead) error("offline")
            if (request.method == "GET") apiSuccess("""{"policies":[{"id":"ai","name":"AI"}]}""")
            else throw ModuleApiException("policy.runtime_sync_failed", "saved but runtime reload failed")
        }))
        val viewModel = PolicyViewModel(repository)

        viewModel.refresh()
        withTimeout(5_000) { viewModel.state.first { !it.loading } }
        assertEquals("offline", viewModel.state.value.error)
        failRead = false
        viewModel.refresh()
        withTimeout(5_000) { viewModel.state.first { !it.loading && it.policies.isNotEmpty() } }
        viewModel.select(viewModel.state.value.policies.single(), "default", "Tokyo")
        withTimeout(5_000) { viewModel.state.first { it.busyId.isEmpty() && it.error.isNotEmpty() } }
        assertTrue(viewModel.state.value.error.contains("runtime reload failed"))
        assertFalse(viewModel.state.value.loading)
    }
}
