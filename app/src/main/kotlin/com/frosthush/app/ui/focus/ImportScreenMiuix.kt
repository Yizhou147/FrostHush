package com.frosthush.app.ui.focus

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.frosthush.app.BuildConfig
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.ui.AppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
import top.yukonga.miuix.kmp.icon.extended.Paste
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 导入页 · miuix 版（HyperOS 设计语言）：
 * TabRow 分段切换「手动导入 / 剪贴板导入」。
 * 手动导入：应用列表 + 搜索（名称/包名/拼音）+ 分类筛选 + 全选/清空 + 已选计数。
 * 剪贴板导入：解析剪贴板包名列表，默认全选。
 */
@Composable
fun ImportScreenMiuix(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    // 后台线程加载：queryApps 在 Shizuku 可用时会跨用户读取分身（binder IPC），不能放主线程
    var allApps by remember { mutableStateOf<List<AppRepository.AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        allApps = withContext(Dispatchers.Default) { repo.queryApps() }
    }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 手动 1 剪贴板

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        // 背景明确为 miuix 表面色；顶部状态栏由 TopAppBar 自带 insets 处理，
        // 底部不给手势条留空白（对齐雹全屏内容）
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.import_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.back))
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
                .padding(padding),
        ) {
            TabRow(
                tabs = listOf(
                    stringResource(R.string.import_manual),
                    stringResource(R.string.import_clipboard),
                ),
                selectedTabIndex = mode,
                onTabSelected = { mode = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            when (mode) {
                0 -> ManualImportTab(repo = repo, allApps = allApps, onAdd = { pkgs -> addToBlacklist(pkgs); onBack() })
                else -> ClipboardImportTab(context = context, repo = repo, onAdd = { pkgs -> addToBlacklist(pkgs); onBack() })
            }
        }
    }
}

private fun addToBlacklist(packages: Set<String>) {
    // 过滤掉本应用，避免误把自己加入暂停黑名单（条目可能是 包名 或 包名@userId）
    val filtered = packages.filter { FocusStore.parseEntry(it).first != BuildConfig.APPLICATION_ID }.toSet()
    if (filtered.isEmpty()) return
    FocusStore.saveBlacklist((FocusStore.blacklist() + filtered).distinct())
    FocusManager.bumpVersion()
}

/** 手动导入：搜索 + 分类筛选 + 应用列表（已导入的显示已勾选并禁用，不能重复导入） */
@Composable
private fun ManualImportTab(
    repo: AppRepository,
    allApps: List<AppRepository.AppInfo>,
    onAdd: (Set<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(1) } // 1 用户应用（默认） 2 系统应用 3 双开应用
    var selected by remember { mutableStateOf(setOf<String>()) }
    var filtered by remember { mutableStateOf<List<AppRepository.AppInfo>>(emptyList()) }
    // 系统应用筛选需先确认风险（会话内确认一次，取消则停留在当前筛选）
    var showSystemWarning by remember { mutableStateOf(false) }
    var systemConfirmed by remember { mutableStateOf(false) }
    // 已导入黑名单：这些应用已勾选且禁用，不可重复导入
    val blacklist = remember { FocusStore.blacklist().toSet() }

    // 后台线程过滤（FuzzySearch + 拼音搜索）
    LaunchedEffect(allApps, query, filter) {
        filtered = withContext(Dispatchers.Default) {
            val base = when (filter) {
                1 -> allApps.filter { !it.isSystem }
                2 -> allApps.filter { it.isSystem }
                else -> allApps.filter { it.isClone } // 3 用户双开应用
            }
            repo.filter(base, query)
        }
    }

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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
        )
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
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.focus_selected_count, selected.size),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.import_select_all),
                onClick = { selected = filtered.map { it.entry }.filter { it !in blacklist }.toSet() },
            )
            Spacer(Modifier.width(8.dp))
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
                        val alreadyImported = app.entry in blacklist
                        AppCheckRow(
                            packageName = app.packageName,
                            name = app.displayName,
                            checked = alreadyImported || app.entry in selected,
                            enabled = !alreadyImported,
                            onToggle = {
                                selected = if (app.entry in selected) selected - app.entry
                                else selected + app.entry
                            },
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onAdd(selected) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            enabled = selected.isNotEmpty(),
        ) {
            Text(stringResource(R.string.import_add_count, selected.size))
        }
    }
}

/** 剪贴板导入：解析包名列表，默认全选 */
@Composable
private fun ClipboardImportTab(
    context: Context,
    repo: AppRepository,
    onAdd: (Set<String>) -> Unit,
) {
    val installed = remember { mutableStateOf<List<AppRepository.AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        installed.value = withContext(Dispatchers.Default) { repo.queryApps() }
    }
    val installedNames = remember(installed.value) { installed.value.map { it.packageName }.toSet() }
    val nameMap = remember(installed.value) { installed.value.associate { it.packageName to it.name } }
    // 已导入黑名单：显示已勾选并禁用，不参与默认全选
    val blacklist = remember { FocusStore.blacklist().toSet() }
    var packages by remember { mutableStateOf(listOf<String>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    // 应用列表加载完成后解析剪贴板（installed 空时为加载中，跳过）
    LaunchedEffect(installed.value) {
        if (installed.value.isEmpty()) return@LaunchedEffect
        val text = runCatching {
            val clip = context.getSystemService(ClipboardManager::class.java)
            clip?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
        }.getOrDefault(null) ?: return@LaunchedEffect

        val parsed = withContext(Dispatchers.Default) {
            // 兼容雹导出的 JSON 数组格式 ["pkg1","pkg2",...]（截取 [ 到 ]），
            // 无数组时按空格/逗号/分号分隔的纯包名列表解析。
            val pkgs = runCatching {
                if (text.contains('[')) {
                    val json = JSONArray(text.substring(text.indexOf('[')..text.indexOf(']', text.indexOf('['))))
                    (0 until json.length()).map { json.getString(it) }
                } else {
                    text.split(Regex("[\\s,，;；\\n]+"))
                        .map { it.trim() }
                        .filter { it.matches(Regex("[a-zA-Z0-9_.]+")) }
                }
            }.getOrDefault(emptyList())
            pkgs.distinct().filter { it in installedNames }
        }
        packages = parsed
        selected = parsed.filter { it !in blacklist }.toSet() // 默认全选未导入的
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                MiuixIcons.Paste,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.import_clipboard_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (packages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.import_clipboard_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Card(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(packages, key = { it }) { pkg ->
                        val alreadyImported = pkg in blacklist
                        AppCheckRow(
                            packageName = pkg,
                            name = nameMap[pkg] ?: pkg,
                            checked = alreadyImported || pkg in selected,
                            enabled = !alreadyImported,
                            onToggle = {
                                selected = if (pkg in selected) selected - pkg else selected + pkg
                            },
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onAdd(selected) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            enabled = selected.isNotEmpty(),
        ) {
            Text(stringResource(R.string.import_add_count, selected.size))
        }
    }
}

/** 应用列表项：左侧应用图标 + 标题/包名 + 右侧勾选框（已导入的禁用） */
@Composable
private fun AppCheckRow(
    packageName: String,
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    CheckboxPreference(
        title = name,
        summary = packageName,
        checked = checked,
        onCheckedChange = { onToggle() },
        enabled = enabled,
        // 与主界面一致：勾选框在右侧
        checkboxLocation = CheckboxLocation.End,
        startAction = {
            // 已导入（disabled）：图标变灰，与文字禁用形成完整视觉反馈
            Box(Modifier.alpha(if (enabled) 1f else 0.4f)) {
                AppIcon(packageName, 36.dp)
            }
        },
    )
}
