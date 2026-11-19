package com.fanjv.netproxy.feature.routing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fanjv.netproxy.core.api.ModuleApiException
import com.fanjv.netproxy.core.ui.userMessage
import com.fanjv.netproxy.feature.proxy.data.editablePolicyTypes
import com.fanjv.netproxy.feature.proxy.data.parsePolicySection
import com.fanjv.netproxy.feature.proxy.data.text
import com.fanjv.netproxy.feature.routing.model.LocalRuleDocument
import com.fanjv.netproxy.feature.routing.model.LocalRuleSet
import com.fanjv.netproxy.feature.routing.model.RuleField
import com.fanjv.netproxy.feature.routing.model.RuleInputError
import com.fanjv.netproxy.feature.routing.model.policyRule
import com.fanjv.netproxy.feature.routing.model.simpleRule
import com.fanjv.netproxy.feature.settings.data.ConfigRepository
import com.fanjv.netproxy.feature.settings.model.ConfigSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RuleIdentity(val target: LocalRuleSet, val index: Int)

internal data class RuleDraft(
    val target: LocalRuleSet,
    val origin: RuleIdentity? = null,
    val field: RuleField? = RuleField.DomainSuffix,
    val text: String = "",
    val policyTag: String? = null,
) {
    val index: Int? get() = origin?.takeIf { it.target == target }?.index
}

internal data class RoutingPolicyTarget(val tag: String, val type: String)

internal data class RuleFileState(
    val document: LocalRuleDocument? = null,
    val revision: String = "",
    val requiresReload: Boolean = false,
    val invalidDocument: Boolean = false,
    val error: String = "",
)

internal data class RoutingRulesState(
    val files: Map<LocalRuleSet, RuleFileState> = LocalRuleSet.entries.associateWith { RuleFileState() },
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val draft: RuleDraft? = null,
    val deleting: RuleIdentity? = null,
    val inputError: RuleInputError? = null,
    val policyTargets: List<RoutingPolicyTarget> = emptyList(),
) {
    val rules: List<RuleIdentity> get() = LocalRuleSet.entries.flatMap { target ->
        files.getValue(target).document?.editableIndices?.map { RuleIdentity(target, it) }.orEmpty()
    }
    val hasDocuments: Boolean get() = files.values.any { it.document != null }
    val isEmpty: Boolean get() = rules.isEmpty() && files.values.all { it.document != null && !it.requiresReload && it.error.isEmpty() }
    val canAdd: Boolean get() = LocalRuleSet.entries.any(::canAddTo)

    fun canAddTo(target: LocalRuleSet): Boolean = editable(target) && (target != LocalRuleSet.Policy ||
        (policyTargets.isNotEmpty() && files.getValue(target).document?.policyStart != null))

    fun canChangeTarget(draft: RuleDraft): Boolean = draft.field != null && !isLoading && !isSaving &&
        (draft.origin == null || editable(draft.origin.target))

    fun canSave(draft: RuleDraft): Boolean = editable(draft.target) &&
        (draft.origin == null || editable(draft.origin.target)) && (draft.target != LocalRuleSet.Policy ||
        (policyTargets.any { it.tag == draft.policyTag } &&
            (draft.index != null || files.getValue(draft.target).document?.policyStart != null)))

    fun showPageError(target: LocalRuleSet): Boolean = target != draft?.target && target != draft?.origin?.target &&
        target != deleting?.target &&
        files.getValue(target).let { it.error.isNotEmpty() || it.requiresReload }

    fun editable(target: LocalRuleSet): Boolean = files.getValue(target).let {
        it.document != null && !isLoading && !isSaving && !it.requiresReload
    }

    fun withFile(target: LocalRuleSet, file: RuleFileState): RoutingRulesState = copy(files = files + (target to file))
}

internal class RoutingRulesViewModel(
    private val repository: ConfigRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val mutableState = MutableStateFlow(RoutingRulesState())
    val state = mutableState.asStateFlow()

    fun selectTarget(target: LocalRuleSet) {
        val current = state.value
        val draft = current.draft ?: return
        if (!current.canChangeTarget(draft)) return
        mutableState.update {
            it.copy(draft = draft.copy(target = target), inputError = null)
        }
    }

    fun selectPolicy(tag: String) {
        val current = state.value
        val draft = current.draft ?: return
        if (draft.target != LocalRuleSet.Policy || current.isSaving || current.policyTargets.none { it.tag == tag }) return
        mutableState.update { it.copy(draft = draft.copy(policyTag = tag)) }
    }

    fun refresh(discardDraft: Boolean = false) {
        val current = state.value
        if (current.isLoading || current.isSaving || (!discardDraft && (current.draft != null || current.deleting != null))) return
        mutableState.update { it.copy(isLoading = true, draft = null, deleting = null, inputError = null) }
        viewModelScope.launch {
            try {
                val files = LocalRuleSet.entries.associateWith { target ->
                    try {
                        val snapshot = repository.readSnapshot(target.target)
                        val document = try {
                            withContext(Dispatchers.Default) { LocalRuleDocument.parse(snapshot.content, target == LocalRuleSet.Policy) }
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            null
                        }
                        RuleFileState(document, snapshot.revision, requiresReload = document == null, invalidDocument = document == null)
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        current.files.getValue(target).copy(requiresReload = true, error = error.userMessage())
                    }
                }
                val policyTargets = try {
                    parsePolicySection(repository.read("singbox/outbounds"), "outbounds")
                        .mapNotNull {
                            val type = it.text("type")
                            val tag = it.text("tag")
                            if (tag.isNotBlank() && type in editablePolicyTypes) RoutingPolicyTarget(tag, type) else null
                        }.distinctBy { it.tag }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    emptyList()
                }
                mutableState.update { it.copy(files = files, policyTargets = policyTargets) }
            } finally {
                mutableState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun add() {
        val current = state.value
        if (current.draft != null || current.deleting != null) return
        val target = LocalRuleSet.entries.firstOrNull { current.canAddTo(it) } ?: return
        mutableState.update { it.copy(draft = RuleDraft(target), inputError = null)
            .withFile(target, it.files.getValue(target).copy(error = "")) }
    }

    fun edit(identity: RuleIdentity) {
        val current = state.value
        if (!current.editable(identity.target) || current.draft != null || current.deleting != null) return
        val document = current.files.getValue(identity.target).document!!
        if (identity.index !in document.editableIndices) return
        val rule = document.rules[identity.index]
        val policy = if (identity.target == LocalRuleSet.Policy) rule.policyRule() else null
        val simple = policy?.second ?: rule.simpleRule()
        mutableState.update { it.copy(draft = RuleDraft(identity.target, identity, simple?.field,
            simple?.values?.joinToString("\n").orEmpty(), policy?.first), inputError = null)
            .withFile(identity.target, it.files.getValue(identity.target).copy(error = "")) }
    }

    fun updateDraft(field: RuleField, text: String) {
        val current = state.value
        val draft = current.draft ?: return
        if (current.isSaving || draft.field == null) return
        mutableState.update {
            val file = it.files.getValue(draft.target)
            it.copy(draft = draft.copy(field = field, text = text), inputError = null)
                .withFile(draft.target, file.copy(error = if (file.requiresReload) file.error else ""))
        }
    }

    fun requestDelete(identity: RuleIdentity) {
        val current = state.value
        if (!current.editable(identity.target) || identity.index !in current.files.getValue(identity.target).document!!.editableIndices) return
        mutableState.update { it.copy(draft = null, deleting = identity, inputError = null)
            .withFile(identity.target, it.files.getValue(identity.target).copy(error = "")) }
    }

    fun dismiss() {
        if (!state.value.isSaving) mutableState.update { it.copy(draft = null, deleting = null, inputError = null) }
    }

    fun save() {
        val current = state.value
        val draft = current.draft ?: return
        val field = draft.field ?: return
        if (!current.canSave(draft)) return
        val error = field.inputError(draft.text)
        if (error != null) {
            mutableState.update { it.copy(inputError = error) }
            return
        }
        val rule = if (draft.target == LocalRuleSet.Policy) field.policyRule(draft.text, requireNotNull(draft.policyTag))
            else field.rule(draft.text)
        val destination = current.files.getValue(draft.target).document!!.replace(draft.index, rule)
        val origin = draft.origin
        val documents = if (origin != null && origin.target != draft.target) {
            val source = current.files.getValue(origin.target).document!!.remove(origin.index)
            linkedMapOf(origin.target to source, draft.target to destination)
        } else {
            mapOf(draft.target to destination)
        }
        apply(draft.target, documents)
    }

    fun delete() {
        val current = state.value
        val identity = current.deleting ?: return
        if (!current.editable(identity.target)) return
        val document = current.files.getValue(identity.target).document!!.remove(identity.index)
        apply(identity.target, mapOf(identity.target to document))
    }

    private fun apply(target: LocalRuleSet, documents: Map<LocalRuleSet, LocalRuleDocument>) {
        val current = state.value
        val files = current.files.filterKeys { it in documents }
        mutableState.update {
            it.copy(isSaving = true, files = it.files + files.mapValues { (_, file) -> file.copy(error = "") })
        }
        viewModelScope.launch {
            try {
                val edits = withContext(Dispatchers.Default) {
                    documents.mapValues { (key, document) ->
                        ConfigSnapshot(document.content(), files.getValue(key).revision)
                    }
                }
                val destination = edits.getValue(target)
                val source = edits.keys.singleOrNull { it != target }
                val revisions = if (source == null) {
                    mapOf(target.target to repository.apply(target.target, destination.content, destination.revision))
                } else {
                    repository.moveRoutingRule(source.target, edits.getValue(source), target.target, destination)
                }
                val saved = documents.mapValues { (key, document) -> RuleFileState(document, revisions.getValue(key.target)) }
                mutableState.update { it.copy(isSaving = false, draft = null, deleting = null, inputError = null,
                    files = it.files + saved) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                val unchanged = if ((error as? ModuleApiException)?.resultCode == "config.conflict") false else try {
                    files.all { (key, file) -> repository.readSnapshot(key.target).revision == file.revision }
                } catch (readError: Exception) {
                    if (readError is CancellationException) throw readError
                    false
                }
                // 仅确认原 revision 未变化时允许修正草稿重试，不拿新 revision 覆盖旧快照。
                val failed = files.mapValues { (key, file) ->
                    file.copy(requiresReload = !unchanged, error = if (key == target) error.userMessage() else "")
                }
                mutableState.update { it.copy(isSaving = false, files = it.files + failed) }
            }
        }
    }
}
