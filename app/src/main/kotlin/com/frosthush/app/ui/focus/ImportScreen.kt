package com.frosthush.app.ui.focus

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
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

/**
 * 导入页：FilterChip 切换「手动导入 / 剪贴板导入」。
 * 手动导入：应用列表 + 搜索（名称/包名/拼音）+ 分类筛选（全部/用户/系统）+ 全选/清空 + 已选计数。
 * 剪贴板导入：解析剪贴板包名列表，默认全选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    val allApps = remember { repo.queryApps() }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 手动 1 剪贴板

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    label = { Text(stringResource(R.string.import_manual)) },
                )
                FilterChip(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    label = { Text(stringResource(R.string.import_clipboard)) },
                )
            }
            when (mode) {
                0 -> ManualImportTab(repo = repo, allApps = allApps, onAdd = { pkgs -> addToBlacklist(pkgs); onBack() })
                else -> ClipboardImportTab(context = context, repo = repo, onAdd = { pkgs -> addToBlacklist(pkgs); onBack() })
            }
        }
    }
}

private fun addToBlacklist(packages: Set<String>) {
    // 过滤掉本应用，避免误把自己加入暂停黑名单
    val filtered = packages.filter { it != BuildConfig.APPLICATION_ID }.toSet()
    if (filtered.isEmpty()) return
    FocusStore.saveBlacklist((FocusStore.blacklist() + filtered).distinct())
    FocusManager.bumpVersion()
}

/** 手动导入：搜索 + 分类筛选 + 应用列表 */
@Composable
private fun ManualImportTab(
    repo: AppRepository,
    allApps: List<AppRepository.AppInfo>,
    onAdd: (Set<String>) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(1) } // 0 全部 1 用户应用（默认） 2 系统应用
    var selected by remember { mutableStateOf(setOf<String>()) }
    var filtered by remember { mutableStateOf<List<AppRepository.AppInfo>>(emptyList()) }

    // 后台线程过滤（FuzzySearch + 拼音搜索）
    LaunchedEffect(allApps, query, filter) {
        filtered = withContext(Dispatchers.Default) {
            val base = when (filter) {
                0 -> allApps
                1 -> allApps.filter { !it.isSystem }
                else -> allApps.filter { it.isSystem }
            }
            repo.filter(base, query)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
            FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text(stringResource(R.string.import_filter_all)) })
            FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text(stringResource(R.string.import_filter_user)) })
            FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text(stringResource(R.string.import_filter_system)) })
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.focus_selected_count, selected.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { selected = filtered.map { it.packageName }.toSet() }) {
                Text(stringResource(R.string.import_select_all))
            }
            TextButton(onClick = { selected = emptySet() }) {
                Text(stringResource(R.string.import_clear))
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
                items(filtered, key = { it.packageName }) { app ->
                    AppCheckRow(
                        packageName = app.packageName,
                        name = app.name,
                        checked = app.packageName in selected,
                        onToggle = {
                            selected = if (app.packageName in selected) selected - app.packageName
                            else selected + app.packageName
                        },
                    )
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
    val installed = remember { repo.queryApps() }
    val installedNames = remember(installed) { installed.map { it.packageName }.toSet() }
    val nameMap = remember(installed) { installed.associate { it.packageName to it.name } }
    var packages by remember { mutableStateOf(listOf<String>()) }
    var selected by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(Unit) {
        val text = runCatching {
            val clip = context.getSystemService(ClipboardManager::class.java)
            clip?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
        }.getOrDefault(null) ?: return@LaunchedEffect

        val parsed = withContext(Dispatchers.Default) {
            text.split(Regex("[\\s,，;；\\n]+"))
                .map { it.trim() }
                .filter { it.matches(Regex("[a-zA-Z0-9_.]+")) }
                .distinct()
                .filter { it in installedNames }
        }
        packages = parsed
        selected = parsed.toSet() // 默认全选
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.import_clipboard_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (packages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.import_clipboard_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(packages, key = { it }) { pkg ->
                    AppCheckRow(
                        packageName = pkg,
                        name = nameMap[pkg] ?: pkg,
                        checked = pkg in selected,
                        onToggle = {
                            selected = if (pkg in selected) selected - pkg else selected + pkg
                        },
                    )
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

@Composable
private fun AppCheckRow(
    packageName: String,
    name: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
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
        if (checked) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
