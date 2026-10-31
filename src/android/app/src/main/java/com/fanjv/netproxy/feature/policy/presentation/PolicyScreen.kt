package com.fanjv.netproxy.feature.policy.presentation

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanjv.netproxy.R
import com.fanjv.netproxy.core.di.netProxyViewModel
import com.fanjv.netproxy.core.ui.component.BlurredBar
import com.fanjv.netproxy.core.ui.component.TopBarMenuAction
import com.fanjv.netproxy.core.ui.component.TopBarMoreMenu
import com.fanjv.netproxy.core.ui.component.rememberBlurBackdrop
import com.fanjv.netproxy.feature.catalog.model.CatalogNode
import com.fanjv.netproxy.feature.catalog.model.CatalogNodeGroup
import com.fanjv.netproxy.feature.catalog.model.NodeDelayResult
import com.fanjv.netproxy.feature.catalog.presentation.nodes.list.components.NodeCard
import com.fanjv.netproxy.feature.policy.model.PolicyModel
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

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
    var pickerPolicyId by remember { mutableStateOf<String?>(null) }
    var pickerGroupId by remember { mutableStateOf("") }
    var expandedPolicies by remember {
        mutableStateOf(displayPreferences.getStringSet("expanded_policies", emptySet()).orEmpty())
    }
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

    fun togglePolicy(policyId: String) {
        val next = if (policyId in expandedPolicies) expandedPolicies - policyId else expandedPolicies + policyId
        expandedPolicies = next
        displayPreferences.edit { putStringSet("expanded_policies", next) }
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
        when {
            state.loading && state.policies.isEmpty() -> {
                Text(stringResource(R.string.policies_loading), modifier = Modifier.padding(padding).padding(20.dp))
            }
            state.error.isNotBlank() && state.policies.isEmpty() -> {
                Text(state.error, modifier = Modifier.padding(padding).padding(20.dp), color = colorScheme.error)
            }
            state.policies.isEmpty() -> {
                Text(stringResource(R.string.policy_empty), modifier = Modifier.padding(padding).padding(20.dp))
            }
            else -> {
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
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            // Keep the first card below the large top-app-bar title.
                            top = padding.calculateTopPadding() + 56.dp,
                            end = 12.dp,
                            bottom = padding.calculateBottomPadding() + bottomPadding + 84.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (state.error.isNotBlank()) item { Text(state.error, color = colorScheme.error) }
                        items(state.policies, key = PolicyModel::id) { policy ->
                            PolicyCard(
                                policy = policy,
                                expanded = policy.id in expandedPolicies,
                                busy = state.busyId == policy.id,
                                delay = state.delays[policy.id],
                                columns = layoutDensity + 1,
                                itemSize = itemSize,
                                sortMode = sortMode,
                                onToggleExpanded = { togglePolicy(policy.id) },
                                onChooseNodes = {
                                    pickerPolicyId = policy.id
                                    pickerGroupId = policy.groupId
                                },
                                onSelect = viewModel::select,
                                onTest = viewModel::test
                            )
                        }
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

    val pickerPolicy = state.policies.firstOrNull { it.id == pickerPolicyId }
    OverlayBottomSheet(
        show = pickerPolicy != null,
        title = pickerPolicy?.name.orEmpty(),
        onDismissRequest = { pickerPolicyId = null }
    ) {
        if (pickerPolicy != null) {
            PolicyNodePicker(
                policy = pickerPolicy,
                groupId = pickerGroupId,
                delay = state.delays[pickerPolicy.id],
                columns = layoutDensity + 1,
                itemSize = itemSize,
                sortMode = sortMode,
                busy = state.busyId == pickerPolicy.id,
                onGroupSelected = { pickerGroupId = it },
                onSelect = { groupId, nodeTag, mode ->
                    viewModel.select(pickerPolicy, groupId, nodeTag, mode)
                    pickerPolicyId = null
                },
                onTest = { viewModel.test(pickerPolicy) }
            )
        }
    }
}

@Composable
private fun PolicyCard(
    policy: PolicyModel,
    expanded: Boolean,
    busy: Boolean,
    delay: NodeDelayResult?,
    columns: Int,
    itemSize: Int,
    sortMode: Int,
    onToggleExpanded: () -> Unit,
    onChooseNodes: () -> Unit,
    onSelect: (PolicyModel, String, String, String) -> Unit,
    onTest: (PolicyModel) -> Unit
) {
    val group = policy.groups.firstOrNull { it.group.id == policy.groupId }
    val usesNodes = policy.mode == "auto" || policy.mode == "manual"
    Card(modifier = Modifier.fillMaxWidth().animateContentSize(), onClick = onToggleExpanded) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PolicyIcon(policy)
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(policy.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        policy.ruleSets.joinToString(" · ").ifBlank { policy.type },
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) MiuixIcons.ExpandLess else MiuixIcons.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.policy_collapse else R.string.policy_expand),
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurfaceVariantActions
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        policyTargetLabel(policy),
                        color = colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        policySelectionSummary(policy, group),
                        fontSize = 13.sp,
                        color = colorScheme.onSurfaceVariantActions,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                RuntimeBadge(policy.runtimeSynced)
            }
            if (expanded) {
                val modeItems = buildList {
                    add("direct" to stringResource(R.string.policy_direct))
                    add("proxy" to stringResource(R.string.policy_proxy))
                    if (policy.chainAvailable || policy.mode == "chain") {
                        add("chain" to stringResource(R.string.policy_chain))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    modeItems.forEach { (mode, label) ->
                        TextButton(
                            text = label,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            colors = if (policy.mode == mode) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors(),
                            onClick = { onSelect(policy, policy.groupId, "", mode) }
                        )
                    }
                }
                if (usesNodes) {
                    if (policy.groups.isNotEmpty()) {
                        OverlayDropdownPreference(
                            title = stringResource(R.string.policy_group_picker),
                            items = policy.groups.map { groupItem ->
                                if (groupItem.group.id == "default") stringResource(R.string.node_local_config)
                                else groupItem.group.name
                            },
                            selectedIndex = policy.groups.indexOfFirst { it.group.id == policy.groupId }.coerceAtLeast(0),
                            onSelectedIndexChange = { index ->
                                policy.groups.getOrNull(index)?.let { selected ->
                                    onSelect(policy, selected.group.id, "", "auto")
                                }
                            }
                        )
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.policy_current_selection),
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariantSummary
                            )
                            Text(
                                policySelectionSummary(policy, group),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            text = stringResource(R.string.policy_choose_node),
                            modifier = Modifier.weight(1f),
                            enabled = !busy && group != null,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = onChooseNodes
                        )
                        TextButton(
                            text = stringResource(R.string.policy_test),
                            modifier = Modifier.weight(1f),
                            enabled = !busy && group?.nodes?.isNotEmpty() == true,
                            onClick = { onTest(policy) }
                        )
                    }
                } else if (policy.mode == "chain" && !policy.chainAvailable) {
                    Text(stringResource(R.string.policy_chain_unavailable), color = colorScheme.error, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PolicyNodePicker(
    policy: PolicyModel,
    groupId: String,
    delay: NodeDelayResult?,
    columns: Int,
    itemSize: Int,
    sortMode: Int,
    busy: Boolean,
    onGroupSelected: (String) -> Unit,
    onSelect: (String, String, String) -> Unit,
    onTest: () -> Unit
) {
    val group = policy.groups.firstOrNull { it.group.id == groupId } ?: policy.groups.firstOrNull()
    val selectedGroupIndex = policy.groups.indexOfFirst { it.group.id == group?.group?.id }.coerceAtLeast(0)
    var query by remember(policy.id, group?.group?.id) { mutableStateOf("") }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (policy.groups.isNotEmpty()) {
            OverlayDropdownPreference(
                title = stringResource(R.string.policy_group_picker),
                items = policy.groups.map { groupItem ->
                    if (groupItem.group.id == "default") stringResource(R.string.node_local_config)
                    else groupItem.group.name
                },
                selectedIndex = selectedGroupIndex,
                onSelectedIndexChange = { index ->
                    policy.groups.getOrNull(index)?.group?.id?.let(onGroupSelected)
                }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group?.group?.name.orEmpty().ifBlank { stringResource(R.string.node_local_config) },
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                text = stringResource(R.string.policy_test),
                enabled = !busy && group?.nodes?.isNotEmpty() == true,
                onClick = onTest
            )
        }
        if (group?.nodes?.isNotEmpty() == true) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.policy_search_nodes),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        if (group == null || group.nodes.isEmpty()) {
            Text(stringResource(R.string.policy_no_nodes), color = colorScheme.onSurfaceVariantSummary)
        } else {
            val entries = policyEntries(group, delay, sortMode).filter { node ->
                node == null || query.isBlank() ||
                    node.tag.contains(query, ignoreCase = true) ||
                    node.server.contains(query, ignoreCase = true)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { node -> node?.tag ?: "__auto__" }) { node ->
                    if (node == null) {
                        NodeCard(
                            title = stringResource(R.string.node_auto_fastest),
                            summary = stringResource(R.string.node_auto_test),
                            protocol = "AUTO",
                            selected = policy.mode == "auto",
                            enabled = !busy,
                            itemSize = itemSize,
                            onClick = { onSelect(group.group.id, "", "auto") }
                        )
                    } else {
                        NodeCard(
                            title = node.tag,
                            summary = nodeSummary(node, delay),
                            protocol = node.protocol.uppercase().ifBlank { "NODE" },
                            latency = nodeLatency(node, delay),
                            selected = policy.mode == "manual" && policy.groupId == group.group.id && policy.nodeTag == node.tag,
                            enabled = !busy,
                            itemSize = itemSize,
                            onClick = { onSelect(group.group.id, node.tag, "manual") }
                        )
                    }
                }
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
    else -> stringResource(R.string.policy_manual)
}

@Composable
private fun policySelectionSummary(policy: PolicyModel, group: CatalogNodeGroup?): String = when (policy.mode) {
    "direct" -> stringResource(R.string.policy_direct)
    "proxy" -> stringResource(R.string.policy_proxy)
    "chain" -> stringResource(R.string.policy_chain)
    "auto" -> stringResource(
        R.string.policy_auto_in_group,
        group?.group?.name.orEmpty().ifBlank { stringResource(R.string.node_local_config) }
    )
    else -> if (policy.nodeTag.isBlank()) {
        stringResource(
            R.string.policy_auto_in_group,
            group?.group?.name.orEmpty().ifBlank { stringResource(R.string.node_local_config) }
        )
    } else {
        stringResource(
            R.string.policy_manual_in_group,
            group?.group?.name.orEmpty().ifBlank { stringResource(R.string.node_local_config) },
            policy.nodeTag
        )
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
            color = colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun policyEntries(
    group: CatalogNodeGroup,
    delay: NodeDelayResult?,
    sortMode: Int
): List<CatalogNode?> {
    val nodes = when (sortMode) {
        1 -> group.nodes.sortedBy { it.tag.lowercase() }
        2 -> group.nodes.sortedBy { it.protocol.lowercase() }
        3 -> group.nodes.sortedBy { nodeLatencyValue(it, delay) }
        else -> group.nodes
    }
    return listOf(null) + nodes
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
