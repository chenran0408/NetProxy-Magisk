package com.fanjv.netproxy.feature.policy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.feature.policy.data.PolicyRepository
import com.fanjv.netproxy.feature.policy.model.ChainProxyModel
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class PolicyUiState(
    val policies: List<PolicyModel> = emptyList(),
    val loading: Boolean = false,
    val error: String = "",
    val busyId: String = "",
    val delays: Map<String, NodeDelayResult> = emptyMap(),
    val chain: ChainProxyModel = ChainProxyModel(),
)

internal class PolicyViewModel(private val repository: PolicyRepository) : ViewModel() {
    private val _state = MutableStateFlow(PolicyUiState())
    val state: StateFlow<PolicyUiState> = _state.asStateFlow()
    private var eventsJob: Job? = null
    private var refreshJob: Job? = null
    private var loaded = false

    fun setVisible(visible: Boolean) {
        if (!visible) {
            eventsJob?.cancel()
            eventsJob = null
            refreshJob?.cancel()
            refreshJob = null
            return
        }
        refresh(silent = loaded)
        if (eventsJob?.isActive == true) return
        eventsJob = viewModelScope.launch {
            repository.observeGroups().collect { result ->
                if (result.isSuccess && _state.value.busyId.isBlank()) refresh(silent = true)
            }
        }
    }

    fun refresh(silent: Boolean = false, force: Boolean = false) {
        if (!force && (refreshJob?.isActive == true || _state.value.busyId.isNotBlank())) return
        if (force) refreshJob?.cancel()
        if (!silent) _state.update { it.copy(loading = true, error = "") }
        refreshJob = viewModelScope.launch {
            runCatching { repository.list() }.onSuccess { list ->
                loaded = true
                val chain = runCatching { repository.chain() }.getOrNull() ?: _state.value.chain
                // Keep completed test results visible when the policy list refreshes.
                _state.value = _state.value.copy(policies = list, chain = chain, loading = false, error = "", busyId = "")
            }
                .onFailure { error -> _state.update { it.copy(loading = false, busyId = "", error = error.message.orEmpty()) } }
        }
    }

    fun select(policy: PolicyModel, groupId: String, nodeTag: String, mode: String = if (nodeTag.isBlank()) "auto" else "manual", region: String = "") {
        if (_state.value.busyId.isNotBlank()) return
        _state.update { it.copy(busyId = policy.id, error = "") }
        viewModelScope.launch {
            runCatching { repository.save(policy.copy(groupId = groupId, nodeTag = nodeTag, mode = mode, region = region)) }
                .onSuccess { refresh(force = true) }.onFailure { error -> _state.update { it.copy(error = error.message.orEmpty(), busyId = "") } }
        }
    }

    fun test(policy: PolicyModel) {
        if (_state.value.busyId.isNotBlank()) return
        _state.update { it.copy(busyId = policy.id, error = "") }
        viewModelScope.launch {
            runCatching { repository.test(policy) }.onSuccess { result -> _state.update { it.copy(busyId = "", delays = it.delays + (policy.id to result)) } }
                .onFailure { error -> _state.update { it.copy(error = error.message.orEmpty(), busyId = "") } }
        }
    }

    fun saveChain(front: String, landing: String) {
        if (_state.value.busyId.isNotBlank()) return
        _state.update { it.copy(busyId = CHAIN_BUSY_ID, error = "") }
        viewModelScope.launch {
            runCatching { repository.saveChain(front, landing) }
                .onSuccess { result -> _state.update { it.copy(chain = result, busyId = "") } }
                .onFailure { error -> _state.update { it.copy(error = error.message.orEmpty(), busyId = "") } }
        }
    }

    override fun onCleared() {
        eventsJob?.cancel()
        refreshJob?.cancel()
        super.onCleared()
    }
}

internal const val CHAIN_BUSY_ID = "__chain__"
