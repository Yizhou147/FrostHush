package com.frosthush.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 通用应用选择页 · miuix 版（HyperOS 设计语言）：
 * 搜索（名称/包名/拼音）+ TabRow 分类筛选（用户/双开/系统）+ 勾选多应用，
 * 条目为 包名 或 包名@userId（分身）。确认时通过 onDone 回调返回选中条目。
 * 业务逻辑与 material 版逐字一致，仅替换为 miuix 组件与 token。
 */
@Composable
fun AppSelectScreenMiuix(
    initial: Set<String>,
    onBack: () -> Unit,
    onDone: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    // 应用选择页内系统返回键：退回编辑页而非关闭整个覆盖层
    BackHandler { onBack() }
    // 后台线程加载：queryApps 在 Shizuku 可用时会跨用户读取分身（binder IPC），不能放主线程
    var allApps by remember { mutableStateOf<List<AppRepository.AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.Default) { repo.queryApps() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(1) } // 1 用户应用（默认） 2 系统应用 3 双开应用
    var selected by remember { mutableStateOf(initial) }
    var filtered by remember { mutableStateOf<List<AppRepository.AppInfo>>(emptyList()) }
    // 系统应用筛选需先确认风险（会话内确认一次，取消则停留在当前筛选）
    var showSystemWarning by remember { mutableStateOf(false) }
    var systemConfirmed by remember { mutableStateOf(false) }

    // 后台线程过滤（FuzzySearch + 拼音搜索）
    LaunchedEffect(allApps, query, filter) {
        filtered = withContext(Dispatchers.Default) {
            val base = when (filter) {
                1 -> allApps.filter { !it.isSystem }
                2 -> allApps.filter { it.isSystem }
                else -> allApps.filter { it.isClone }
            }
            repo.filter(base, query)
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        // 背景明确为 miuix 表面色；顶部状态栏由 TopAppBar 自带 insets 处理
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_select_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.import_search_hint),
                useLabelAsPlaceholder = true,
                leadingIcon = {
                    Icon(
                        MiuixIcons.Basic.Search,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            // 分段筛选：用户 / 双开 / 系统（对应 filter 1 / 3 / 2）
            val filterTabs = listOf(
                stringResource(R.string.import_filter_user),
                stringResource(R.string.import_filter_clone),
                stringResource(R.string.import_filter_system),
            )
            val filterIndex = when (filter) {
                1 -> 0
                3 -> 1
                else -> 2
            }
            TabRow(
                tabs = filterTabs,
                selectedTabIndex = filterIndex,
                onTabSelected = { index ->
                    when (index) {
                        0 -> filter = 1
                        1 -> filter = 3
                        // 首次切换到系统应用需先确认风险，确定后才进入
                        else -> if (systemConfirmed) filter = 2 else showSystemWarning = true
                    }
                },
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.focus_selected_count, selected.size),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.import_select_all),
                    onClick = { selected = filtered.map { it.entry }.toSet() },
                )
                TextButton(
                    text = stringResource(R.string.import_clear),
                    onClick = { selected = emptySet() },
                )
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.import_nothing),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            } else {
                Card(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize()) {
                        // key 用条目（主应用=包名，分身=包名@userId），同一包名的主/分身不会冲突
                        items(filtered, key = { it.entry }) { app ->
                            val entry = app.entry
                            SelectAppRowMiuix(
                                packageName = app.packageName,
                                name = app.displayName,
                                checked = entry in selected,
                                onToggle = {
                                    selected = if (entry in selected) selected - entry else selected + entry
                                },
                            )
                        }
                    }
                }
            }
            Button(
                onClick = { onDone(selected) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                enabled = selected.isNotEmpty(),
            ) {
                Text(stringResource(R.string.app_select_confirm, selected.size))
            }
            // 系统应用风险确认弹窗（置于 Scaffold 内容内，由根 Scaffold 的 popup host 渲染）
            OverlayDialog(
                show = showSystemWarning,
                title = stringResource(R.string.import_system_warning_title),
                summary = stringResource(R.string.import_system_warning_text),
                onDismissRequest = { showSystemWarning = false },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = { showSystemWarning = false },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_confirm),
                        onClick = {
                            showSystemWarning = false
                            systemConfirmed = true
                            filter = 2
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 应用列表项：左侧应用图标 + 标题/包名 + 右侧勾选框（miuix CheckboxPreference） */
@Composable
private fun SelectAppRowMiuix(
    packageName: String,
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    CheckboxPreference(
        title = name,
        summary = packageName,
        checked = checked,
        onCheckedChange = { onToggle() },
        // 与主界面一致：勾选框在右侧
        checkboxLocation = CheckboxLocation.End,
        startAction = {
            AppIcon(packageName, 36.dp)
        },
    )
}
