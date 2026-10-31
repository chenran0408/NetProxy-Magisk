package com.fanjv.netproxy.feature.policy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.feature.policy.data.PolicyRepository
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class PolicyUiState(val policies: List<PolicyModel> = emptyList(), val loading: Boolean = false, val error: String = "", val busyId: String = "", val delays: Map<String, NodeDelayResult> = emptyMap())

internal class PolicyViewModel(private val repository: PolicyRepository) : ViewModel() {
    private val _state = MutableStateFlow(PolicyUiState())
    val state: StateFlow<PolicyUiState> = _state.asStateFlow()
    private var eventsJob: Job? = null
    private var loaded = false

    fun setVisible(visible: Boolean) {
        if (!visible) {
            eventsJob?.cancel()
            eventsJob = null
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

    fun refresh(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(loading = true, error = "", busyId = "") }
        viewModelScope.launch {
            runCatching { repository.list() }.onSuccess { list ->
                loaded = true
                _state.value = PolicyUiState(policies = list)
            }
                .onFailure { error -> _state.update { it.copy(loading = false, busyId = "", error = error.message.orEmpty()) } }
        }
    }

    fun select(policy: PolicyModel, groupId: String, nodeTag: String) {
        _state.update { it.copy(busyId = policy.id) }
        viewModelScope.launch {
            runCatching { repository.save(policy.copy(groupId = groupId, nodeTag = nodeTag, mode = if (nodeTag.isBlank()) "auto" else "manual")) }
                .onSuccess { refresh() }.onFailure { error -> _state.update { it.copy(error = error.message.orEmpty(), busyId = "") } }
        }
    }

    fun test(policy: PolicyModel) {
        _state.update { it.copy(busyId = policy.id) }
        viewModelScope.launch {
            runCatching { repository.test(policy) }.onSuccess { result -> _state.update { it.copy(busyId = "", delays = it.delays + (policy.id to result)) } }
                .onFailure { error -> _state.update { it.copy(error = error.message.orEmpty(), busyId = "") } }
        }
    }
}
