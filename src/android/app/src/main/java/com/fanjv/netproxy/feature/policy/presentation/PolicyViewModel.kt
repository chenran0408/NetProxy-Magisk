package com.fanjv.netproxy.feature.policy.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.core.api.ModuleApiException
import com.fanjv.netproxy.feature.policy.data.PolicyRepository
import com.fanjv.netproxy.feature.policy.model.ChainProxyModel
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class PolicyUiState(
    val policies: List<PolicyModel> = emptyList(),
    val loading: Boolean = false,
    val error: String = "",
    val busyId: String = "",
    val delays: Map<String, NodeDelayResult> = emptyMap(),
    val chain: ChainProxyModel = ChainProxyModel(),
    val customSaveCompleted: Boolean = false,
    val savedCustomPolicyId: String? = null,
    val customSaveError: String = "",
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
            try {
                val list = repository.list()
                loaded = true
                val chain = try { repository.chain() }
                    catch (error: CancellationException) { throw error }
                    catch (_: Exception) { _state.value.chain }
                // Keep completed test results visible when the policy list refreshes.
                _state.update { it.copy(policies = list, chain = chain, loading = false, busyId = "") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, busyId = "", error = error.message.orEmpty()) }
            }
        }
    }

    fun select(policy: PolicyModel, groupId: String, nodeTag: String, mode: String = if (nodeTag.isBlank()) "auto" else "manual", region: String = "") {
        if (_state.value.busyId.isNotBlank()) return
        _state.update { it.copy(busyId = policy.id, error = "", delays = it.delays - policy.id) }
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

    fun beginCustomEdit() {
        if (_state.value.busyId.isNotBlank()) return
        _state.update { it.copy(customSaveCompleted = false, savedCustomPolicyId = null, customSaveError = "") }
    }

    fun createCustom(name: String, packages: List<String>, domains: List<String>, ports: List<Int>) =
        saveCustom { repository.create(name, packages, domains, ports) }

    fun updateCustom(policy: PolicyModel, name: String, packages: List<String>, domains: List<String>, ports: List<Int>) =
        saveCustom { repository.updateDefinition(policy, name, packages, domains, ports) }

    private fun saveCustom(save: suspend () -> PolicyModel) {
        if (_state.value.busyId.isNotBlank()) return
        refreshJob?.cancel()
        _state.update { it.copy(busyId = CUSTOM_BUSY_ID, loading = false, error = "",
            customSaveCompleted = false, savedCustomPolicyId = null, customSaveError = "") }
        viewModelScope.launch {
            var warning = ""
            val saved = try {
                save()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error !is ModuleApiException || !error.persisted) {
                    _state.update { it.copy(busyId = "", error = error.message.orEmpty(), customSaveError = error.message.orEmpty()) }
                    return@launch
                }
                // The write committed even though the runtime reload failed. Do not invite a duplicate POST.
                warning = error.message.orEmpty()
                null
            }
            if (saved != null) {
                _state.update { state ->
                    val previous = state.policies.firstOrNull { it.id == saved.id }
                    val policy = saved.copy(
                        groups = previous?.groups ?: state.policies.firstOrNull()?.groups.orEmpty(),
                        chainAvailable = state.chain.available,
                    )
                    state.copy(policies = if (previous == null) state.policies + policy
                        else state.policies.map { if (it.id == policy.id) policy else it },
                        delays = state.delays - policy.id)
                }
            }
            try {
                val list = repository.list()
                loaded = true
                _state.update { it.copy(policies = list) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A failed reread must not remove the card confirmed by the save response.
                warning = listOf(warning, error.message.orEmpty()).filter(String::isNotBlank).joinToString("\n")
            }
            _state.update { it.copy(busyId = "", error = warning, customSaveCompleted = true, savedCustomPolicyId = saved?.id) }
        }
    }

    fun deleteCustom(policy: PolicyModel) {
        if (!policy.isCustom || _state.value.busyId.isNotBlank()) return
        _state.update { it.copy(busyId = CUSTOM_BUSY_ID, error = "") }
        viewModelScope.launch {
            runCatching { repository.delete(policy.id) }
                .onSuccess { refresh(force = true) }
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
internal const val CUSTOM_BUSY_ID = "__custom_policy__"
