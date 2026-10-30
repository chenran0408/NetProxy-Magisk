package com.fanjv.netproxy.feature.policy.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
internal fun PolicyScreen(
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isActive: Boolean = true,
    viewModel: PolicyViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(isActive) { if (isActive) viewModel.refresh() }
    Scaffold(topBar = {
        TopAppBar(
            title = stringResource(R.string.policies),
            actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Rounded.Refresh, stringResource(R.string.common_update)) } }
        )
    }) { padding ->
        if (state.loading && state.policies.isEmpty()) {
            Text(stringResource(R.string.policies_loading), modifier = Modifier.padding(padding).padding(20.dp))
        } else if (state.error.isNotBlank() && state.policies.isEmpty()) {
            Text(state.error, modifier = Modifier.padding(padding).padding(20.dp), color = colorScheme.error)
        } else if (state.policies.isEmpty()) {
            Text(stringResource(R.string.policy_empty), modifier = Modifier.padding(padding).padding(20.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, top = padding.calculateTopPadding() + 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding() + bottomPadding + 84.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.error.isNotBlank()) item { Text(state.error, color = colorScheme.error) }
                items(state.policies, key = PolicyModel::id) { policy -> PolicyCard(policy, state.busyId == policy.id, state.delays[policy.id], viewModel::select, viewModel::test) }
            }
        }
    }
}

@Composable
private fun PolicyCard(policy: PolicyModel, busy: Boolean, delay: NodeDelayResult?, onSelect: (PolicyModel, String, String) -> Unit, onTest: (PolicyModel) -> Unit) {
    val group = policy.groups.firstOrNull { it.group.id == policy.groupId }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(policy.icon, fontSize = 18.sp, modifier = Modifier.padding(end = 10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(policy.name, fontSize = 17.sp)
                    Text("${policy.type} · ${policy.ruleSets.joinToString()}", color = colorScheme.onSurfaceVariantSummary)
                }
                Text(if (policy.mode == "auto") "Auto" else policy.nodeTag, color = colorScheme.primary)
            }
            Text(stringResource(R.string.policy_group, group?.group?.name ?: policy.groupId), color = colorScheme.onSurfaceVariantSummary)
            Text(
                stringResource(if (policy.runtimeSynced) R.string.policy_runtime_active else R.string.policy_runtime_inactive),
                color = if (policy.runtimeSynced) colorScheme.primary else colorScheme.onSurfaceVariantSummary
            )
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                policy.groups.forEach { item ->
                    TextButton(
                        text = item.group.name,
                        enabled = !busy,
                        onClick = { onSelect(policy, item.group.id, "") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(text = stringResource(R.string.policy_auto), enabled = !busy, onClick = { onSelect(policy, policy.groupId, "") })
                TextButton(text = stringResource(R.string.policy_test), enabled = !busy, onClick = { onTest(policy) })
            }
            delay?.groups?.flatMap { it.items }?.takeIf { it.isNotEmpty() }?.let { results ->
                Text(
                    results.joinToString(" · ") { item ->
                        "${item.tag}: ${item.urlTestDelay?.let { "$it ms" } ?: "--"}"
                    },
                    color = colorScheme.onSurfaceVariantSummary
                )
            }
            group?.nodes?.forEach { node ->
                val result = delay?.groups?.flatMap { it.items }?.firstOrNull { it.tag == node.tag }
                val health = when {
                    result == null || result.urlTestTime == null -> R.string.policy_node_not_tested
                    result.urlTestDelay != null -> R.string.policy_node_healthy
                    else -> R.string.policy_node_unhealthy
                }
                TextButton(
                    text = "${node.tag} · ${stringResource(health)}",
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelect(policy, policy.groupId, node.tag) }
                )
            }
        }
    }
}
