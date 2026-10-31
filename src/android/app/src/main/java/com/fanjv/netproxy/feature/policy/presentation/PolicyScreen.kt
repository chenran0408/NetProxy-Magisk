package com.fanjv.netproxy.feature.policy.presentation

import android.content.Context
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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.core.content.edit
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.feature.catalog.model.CatalogNode
import com.fanjv.netproxy.feature.catalog.model.CatalogNodeGroup
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import com.fanjv.netproxy.feature.catalog.presentation.nodes.list.components.NodeCard
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import com.fanjv.netproxy.core.ui.component.BlurredBar
import com.fanjv.netproxy.core.ui.component.rememberBlurBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import com.fanjv.netproxy.core.ui.component.TopBarMenuAction
import com.fanjv.netproxy.core.ui.component.TopBarMoreMenu

@Composable
internal fun PolicyScreen(
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    isActive: Boolean = true,
    viewModel: PolicyViewModel = netProxyViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayPreferences = remember(context) {
        context.getSharedPreferences("node_display", Context.MODE_PRIVATE)
    }
    var showDisplaySettings by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var layoutDensity by remember {
        mutableIntStateOf(
            displayPreferences.getInt(
                "density",
                displayPreferences.getInt("columns", 2) - 1
            ).coerceIn(0, 2)
        )
    }
    var itemSize by remember {
        mutableIntStateOf(displayPreferences.getInt("item_size", 0).coerceIn(0, 2))
    }
    var sortMode by remember {
        mutableIntStateOf(displayPreferences.getInt("sort", 0).coerceIn(0, 3))
    }
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val backdrop = rememberBlurBackdrop()
    val barColor = if (backdrop != null) Color.Transparent else colorScheme.surface
    val refreshTexts = listOf(
        stringResource(R.string.refresh_pulling),
        stringResource(R.string.refresh_release),
        stringResource(R.string.refresh_refresh),
        stringResource(R.string.refresh_complete),
    )
    LifecycleResumeEffect(isActive) {
        viewModel.setVisible(isActive)
        onPauseOrDispose { if (isActive) viewModel.setVisible(false) }
    }
    Scaffold(topBar = {
        BlurredBar(backdrop) {
            TopAppBar(
                color = barColor,
                title = stringResource(R.string.policies),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(enabled = !state.loading && state.busyId.isBlank(), onClick = viewModel::refresh) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.common_update))
                    }
                    TopBarMoreMenu(
                        expanded = showMoreMenu,
                        onExpandedChange = { showMoreMenu = it },
                        actions = listOf(
                            TopBarMenuAction(
                                text = stringResource(R.string.node_display_settings),
                                onClick = { showDisplaySettings = true }
                            )
                        ),
                        contentDescription = stringResource(R.string.more_actions)
                    )
                }
            )
        }
    }) { padding ->
        if (state.loading && state.policies.isEmpty()) {
            Text(stringResource(R.string.policies_loading), modifier = Modifier.padding(padding).padding(20.dp))
        } else if (state.error.isNotBlank() && state.policies.isEmpty()) {
            Text(state.error, modifier = Modifier.padding(padding).padding(20.dp), color = colorScheme.error)
        } else if (state.policies.isEmpty()) {
            Text(stringResource(R.string.policy_empty), modifier = Modifier.padding(padding).padding(20.dp))
        } else {
            PullToRefresh(
                isRefreshing = state.loading,
                onRefresh = viewModel::refresh,
                pullToRefreshState = pullToRefreshState,
                topAppBarScrollBehavior = scrollBehavior,
                refreshTexts = refreshTexts,
                contentPadding = PaddingValues(top = padding.calculateTopPadding())
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = padding.calculateBottomPadding() + bottomPadding + 84.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.error.isNotBlank()) item { Text(state.error, color = colorScheme.error) }
                    items(state.policies, key = PolicyModel::id) { policy ->
                        PolicyCard(
                            policy = policy,
                            busy = state.busyId == policy.id,
                            delay = state.delays[policy.id],
                            columns = layoutDensity + 1,
                            itemSize = itemSize,
                            sortMode = sortMode,
                            onSelect = viewModel::select,
                            onTest = viewModel::test
                        )
                    }
                }
            }
        }
    }
    OverlayBottomSheet(
        show = showDisplaySettings,
        title = stringResource(R.string.node_display_settings),
        onDismissRequest = { showDisplaySettings = false }
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OverlayDropdownPreference(
                title = stringResource(R.string.node_sort_default),
                items = listOf(
                    stringResource(R.string.node_sort_default),
                    stringResource(R.string.node_sort_name),
                    stringResource(R.string.node_sort_protocol),
                    stringResource(R.string.node_sort_latency)
                ),
                selectedIndex = sortMode,
                onSelectedIndexChange = { index ->
                    sortMode = index
                    displayPreferences.edit { putInt("sort", index) }
                }
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.node_density),
                items = listOf(
                    stringResource(R.string.node_density_spacious),
                    stringResource(R.string.node_density_standard),
                    stringResource(R.string.node_density_compact)
                ),
                selectedIndex = layoutDensity,
                onSelectedIndexChange = { index ->
                    layoutDensity = index
                    displayPreferences.edit { putInt("density", index) }
                }
            )
            OverlayDropdownPreference(
                title = stringResource(R.string.node_card_size),
                items = listOf(
                    stringResource(R.string.node_card_size_standard),
                    stringResource(R.string.node_card_size_compact),
                    stringResource(R.string.node_card_size_minimal)
                ),
                selectedIndex = itemSize,
                onSelectedIndexChange = { index ->
                    itemSize = index
                    displayPreferences.edit { putInt("item_size", index) }
                }
            )
            TextButton(
                text = stringResource(R.string.common_done),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { showDisplaySettings = false }
            )
        }
    }
}

@Composable
private fun PolicyCard(
    policy: PolicyModel,
    busy: Boolean,
    delay: NodeDelayResult?,
    columns: Int,
    itemSize: Int,
    sortMode: Int,
    onSelect: (PolicyModel, String, String, String) -> Unit,
    onTest: (PolicyModel) -> Unit
) {
    val group = policy.groups.firstOrNull { it.group.id == policy.groupId }
    val usesNodes = policy.mode == "auto" || policy.mode == "manual"
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
                    policyTargetLabel(policy),
                    color = colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            RuntimeBadge(policy.runtimeSynced)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "direct" to stringResource(R.string.policy_direct),
                    "proxy" to stringResource(R.string.policy_proxy),
                    "chain" to stringResource(R.string.policy_chain)
                ).forEach { (mode, label) ->
                    if (mode == "chain" && !policy.chainAvailable && policy.mode != "chain") return@forEach
                    TextButton(
                        text = label,
                        enabled = !busy && (mode != "chain" || policy.chainAvailable),
                        colors = if (policy.mode == mode) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        onClick = { onSelect(policy, policy.groupId, "", mode) }
                    )
                }
            }
            if (usesNodes) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    policy.groups.forEach { item ->
                        TextButton(
                            text = item.group.name,
                            enabled = !busy,
                            colors = if (policy.mode == "manual" && item.group.id == policy.groupId) {
                                ButtonDefaults.textButtonColorsPrimary()
                            } else {
                                ButtonDefaults.textButtonColors()
                            },
                            onClick = { onSelect(policy, item.group.id, "", "auto") }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.policy_auto),
                        enabled = !busy,
                        colors = if (policy.mode == "auto") ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                        onClick = { onSelect(policy, policy.groupId, "", "auto") }
                    )
                    TextButton(
                        text = stringResource(R.string.policy_test),
                        enabled = !busy,
                        onClick = { onTest(policy) }
                    )
                }
                // Keep the policy node picker in the same compact grid language as Nodes.
                policyEntries(group, delay, sortMode).chunked(columns).forEach { rowEntries ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowEntries.forEach { node ->
                            Box(modifier = Modifier.weight(1f)) {
                                if (node == null) {
                                    NodeCard(
                                        title = stringResource(R.string.node_auto_fastest),
                                        summary = stringResource(R.string.node_auto_test),
                                        protocol = "AUTO",
                                        selected = policy.mode == "auto",
                                        enabled = !busy,
                                        itemSize = itemSize,
                                        onClick = { onSelect(policy, policy.groupId, "", "auto") }
                                    )
                                } else {
                                    NodeCard(
                                        title = node.tag,
                                        summary = nodeSummary(node, delay),
                                        protocol = node.protocol.uppercase().ifBlank { "NODE" },
                                        latency = nodeLatency(node, delay),
                                        selected = policy.mode == "manual" && policy.nodeTag == node.tag,
                                        enabled = !busy,
                                        itemSize = itemSize,
                                        onClick = { onSelect(policy, policy.groupId, node.tag, "manual") }
                                    )
                                }
                            }
                        }
                        if (rowEntries.size < columns) {
                            repeat(columns - rowEntries.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            } else if (policy.mode == "chain" && !policy.chainAvailable) {
                Text(stringResource(R.string.policy_chain_unavailable), color = colorScheme.error, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun policyTargetLabel(policy: PolicyModel): String = when (policy.mode) {
    "direct" -> stringResource(R.string.policy_direct)
    "proxy" -> stringResource(R.string.policy_proxy)
    "chain" -> stringResource(R.string.policy_chain)
    "auto" -> stringResource(R.string.policy_auto)
    else -> policy.nodeTag
}

private fun policyEntries(
    group: CatalogNodeGroup?,
    delay: NodeDelayResult?,
    sortMode: Int
): List<CatalogNode?> {
    if (group == null) return emptyList()
    val nodes = when (sortMode) {
        1 -> group.nodes.sortedBy { it.tag.lowercase() }
        2 -> group.nodes.sortedBy { it.protocol.lowercase() }
        3 -> group.nodes.sortedBy { nodeLatencyValue(it, delay) }
        else -> group.nodes
    }
    return listOf(null) + nodes
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
        "fcm" -> Icons.Rounded.Notifications
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

private fun nodeLatencyValue(node: CatalogNode, delay: NodeDelayResult?): Int =
    nodeResult(node, delay)?.urlTestDelay ?: Int.MAX_VALUE

@Composable
private fun nodeSummary(node: CatalogNode, delay: NodeDelayResult?): String =
    if (nodeLatency(node, delay) == null) stringResource(R.string.policy_node_not_tested) else ""
