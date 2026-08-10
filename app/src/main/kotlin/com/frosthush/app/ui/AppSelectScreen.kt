package com.frosthush.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 通用应用选择页（应用集编辑 / 计划直选复用，逻辑与导入页一致）：
 * 搜索（名称/包名/拼音）+ 分类筛选（用户/系统/双开应用）+ 勾选多应用，
 * 条目为 包名 或 包名@userId（分身）。确认时通过 onDone 回调返回选中条目。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectScreen(
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

    Scaffold(
        // 背景明确为主题背景色；顶部状态栏由 TopAppBar 自带 insets 处理
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_select_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.import_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text(stringResource(R.string.import_filter_user)) })
                    FilterChip(selected = filter == 3, onClick = { filter = 3 }, label = { Text(stringResource(R.string.import_filter_clone)) })
                    FilterChip(
                        selected = filter == 2,
                        onClick = {
                            // 首次切换到系统应用需先确认风险，确定后才进入
                            if (systemConfirmed) filter = 2 else showSystemWarning = true
                        },
                        label = { Text(stringResource(R.string.import_filter_system)) },
                    )
                }
                if (showSystemWarning) {
                    AlertDialog(
                        onDismissRequest = { showSystemWarning = false },
                        title = { Text(stringResource(R.string.import_system_warning_title)) },
                        text = { Text(stringResource(R.string.import_system_warning_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showSystemWarning = false
                                systemConfirmed = true
                                filter = 2
                            }) { Text(stringResource(R.string.action_confirm)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSystemWarning = false }) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        },
                    )
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.focus_selected_count, selected.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { selected = filtered.map { it.entry }.toSet() }) {
                        Text(stringResource(R.string.import_select_all))
                    }
                    TextButton(onClick = { selected = emptySet() }) {
                        Text(stringResource(R.string.import_clear))
                    }
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.import_nothing),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    // key 用条目（主应用=包名，分身=包名@userId），同一包名的主/分身不会冲突
                    items(filtered, key = { it.entry }) { app ->
                        val entry = app.entry
                        SelectAppRow(
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
            Button(
                onClick = { onDone(selected) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                enabled = selected.isNotEmpty(),
            ) {
                Text(stringResource(R.string.app_select_confirm, selected.size))
            }
        }
    }
}

@Composable
private fun SelectAppRow(
    packageName: String,
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // 与主界面一致：点击整行切换选中
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName, 36.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
