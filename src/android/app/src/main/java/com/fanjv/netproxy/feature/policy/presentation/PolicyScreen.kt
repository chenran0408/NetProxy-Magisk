package com.fanjv.netproxy.feature.policy.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.feature.catalog.model.CatalogNode
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import com.fanjv.netproxy.feature.catalog.presentation.nodes.list.components.NodeCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PolicyIcon(policy)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(policy.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        policy.ruleSets.joinToString(" · ").ifBlank { policy.type },
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (policy.mode == "auto") stringResource(R.string.policy_auto) else policy.nodeTag,
                    color = colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RuntimeBadge(policy.runtimeSynced)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                policy.groups.forEach { item ->
                    TextButton(
                        text = item.group.name,
                        enabled = !busy,
                        colors = if (policy.mode != "auto" && item.group.id == policy.groupId) {
                            ButtonDefaults.textButtonColorsPrimary()
                        } else {
                            ButtonDefaults.textButtonColors()
                        },
                        onClick = { onSelect(policy, item.group.id, "") }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = stringResource(R.string.policy_auto),
                    enabled = !busy,
                    colors = if (policy.mode == "auto") ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                    onClick = { onSelect(policy, policy.groupId, "") }
                )
                TextButton(text = stringResource(R.string.policy_test), enabled = !busy, onClick = { onTest(policy) })
            }
            // Keep the policy node picker in the same compact two-column language as Nodes.
            group?.nodes?.chunked(2)?.forEach { rowNodes ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowNodes.forEach { node ->
                        Box(modifier = Modifier.weight(1f)) {
                            NodeCard(
                                title = node.tag,
                                summary = nodeSummary(node, delay),
                                protocol = node.protocol.uppercase().ifBlank { "NODE" },
                                latency = nodeLatency(node, delay),
                                selected = policy.mode != "auto" && policy.nodeTag == node.tag,
                                enabled = !busy,
                                itemSize = 1,
                                onClick = { onSelect(policy, policy.groupId, node.tag) }
                            )
                        }
                    }
                    if (rowNodes.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PolicyIcon(policy: PolicyModel) {
    val icon = when (policy.id) {
        "ai" -> Icons.Rounded.SmartToy
        "youtube" -> Icons.Rounded.PlayCircle
        "tiktok" -> Icons.Rounded.MusicNote
        "netflix" -> Icons.Rounded.Movie
        "github" -> Icons.Rounded.Code
        "google" -> Icons.Rounded.Search
        else -> Icons.Rounded.Tune
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = policy.name, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RuntimeBadge(synced: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (synced) Color(0xFF32A852) else colorScheme.onSurfaceVariantSummary)
        )
        Text(
            stringResource(if (synced) R.string.policy_runtime_active else R.string.policy_runtime_inactive),
            fontSize = 12.sp,
            color = colorScheme.onSurfaceVariantSummary
        )
    }
}

private fun nodeResult(node: CatalogNode, delay: NodeDelayResult?) =
    delay?.groups?.flatMap { it.items }?.firstOrNull { it.tag == node.tag }

private fun nodeLatency(node: CatalogNode, delay: NodeDelayResult?): String? {
    val result = nodeResult(node, delay) ?: return null
    return when {
        result.urlTestTime == null -> null
        result.urlTestDelay != null -> result.urlTestDelay.toString()
        else -> "failed"
    }
}

@Composable
private fun nodeSummary(node: CatalogNode, delay: NodeDelayResult?): String =
    if (nodeLatency(node, delay) == null) stringResource(R.string.policy_node_not_tested) else ""
