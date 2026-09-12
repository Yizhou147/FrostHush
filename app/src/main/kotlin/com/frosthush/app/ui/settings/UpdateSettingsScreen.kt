package com.frosthush.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.frosthush.app.BuildConfig
import com.frosthush.app.R
import com.frosthush.app.data.SettingsStore
import com.frosthush.app.ui.about.openUrl
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode
import com.frosthush.app.update.UpdateChecker
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

/**
 * 检查更新二级页：检查按钮 + 镜像站选择 + 启动自动检查开关 + 当前版本/发布页入口。
 * 双 UI 结构与 ThemeSettingsScreen 一致（分派器 + miuix + material 同文件）。
 */
@Composable
fun UpdateSettingsScreen(onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> UpdateSettingsMiuix(onBack = onBack)
        UiMode.Material -> UpdateSettingsMaterial(onBack = onBack)
    }
}

/** 更新检查共享状态与逻辑（miuix / material 两版共用） */
internal class UpdateCheckState(
    private val onFinished: () -> Unit,
) {
    var checking by mutableStateOf(false)
        private set
    var result by mutableStateOf<UpdateChecker.CheckResult?>(null)

    fun check() {
        if (checking) return
        checking = true
        Thread {
            val mirror = UpdateChecker.UpdateMirror.fromId(SettingsStore.cache.updateMirror)
            val r = UpdateChecker.check(
                BuildConfig.VERSION_NAME,
                mirror,
                SettingsStore.cache.customMirror,
            )
            UpdateChecker.lastResult = r
            SettingsStore.setLastUpdateCheckMillis(System.currentTimeMillis())
            result = r
            checking = false
            onFinished()
        }.start()
    }
}

// ==================== miuix 版 ====================

@Composable
private fun UpdateSettingsMiuix(onBack: () -> Unit) {
    val context = LocalContext.current
    val mirror by SettingsStore.updateMirror.collectAsState(initial = SettingsStore.cache.updateMirror)
    val autoCheck by SettingsStore.autoCheckUpdate.collectAsState(initial = SettingsStore.cache.autoCheckUpdate)
    var showMirrorDialog by remember { mutableStateOf(false) }
    var showCustomMirrorDialog by remember { mutableStateOf(false) }
    var customMirrorInput by remember { mutableStateOf(SettingsStore.cache.customMirror) }
    val state = remember { UpdateCheckState(onFinished = {}) }

    top.yukonga.miuix.kmp.basic.Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            top.yukonga.miuix.kmp.basic.TopAppBar(
                title = stringResource(R.string.about_check_update),
                navigationIcon = {
                    top.yukonga.miuix.kmp.basic.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                ArrowPreference(
                    title = stringResource(R.string.about_check_update),
                    summary = if (state.checking) {
                        stringResource(R.string.update_checking)
                    } else {
                        stringResource(R.string.about_version, BuildConfig.VERSION_NAME)
                    },
                    onClick = { state.check() },
                )
                ArrowPreference(
                    title = stringResource(R.string.update_open_release),
                    summary = "https://github.com/Yizhou147/FrostHush/releases/latest",
                    onClick = {
                        val r = UpdateChecker.lastResult
                        openUrl(
                            context,
                            (r as? UpdateChecker.CheckResult.Available)?.release?.htmlUrl
                                ?: "https://github.com/Yizhou147/FrostHush/releases/latest",
                        )
                    },
                )
            }
            Card {
                OverlayDropdownPreference(
                    items = UpdateChecker.UpdateMirror.entries.map { stringResource(it.labelRes) },
                    selectedIndex = UpdateChecker.UpdateMirror.entries
                        .indexOfFirst { it.id == mirror }.coerceAtLeast(0),
                    title = stringResource(R.string.update_mirror),
                    summary = if (mirror == "custom") {
                        SettingsStore.cache.customMirror.ifBlank {
                            stringResource(R.string.custom_mirror_hint)
                        }
                    } else {
                        stringResource(UpdateChecker.UpdateMirror.fromId(mirror).labelRes)
                    },
                    onSelectedIndexChange = { index ->
                        val selected = UpdateChecker.UpdateMirror.entries[index]
                        SettingsStore.setUpdateMirror(selected.id)
                        if (selected == UpdateChecker.UpdateMirror.CUSTOM) showCustomMirrorDialog = true
                    },
                )
                if (mirror == "custom") {
                    // 自定义镜像：点击该行可随时修改前缀
                    ArrowPreference(
                        title = stringResource(R.string.custom_mirror_title),
                        summary = SettingsStore.cache.customMirror.ifBlank {
                            stringResource(R.string.custom_mirror_hint)
                        },
                        onClick = { showCustomMirrorDialog = true },
                    )
                }
                SwitchPreference(
                    checked = autoCheck,
                    onCheckedChange = { SettingsStore.setAutoCheckUpdate(it) },
                    title = stringResource(R.string.update_auto_check),
                    summary = stringResource(R.string.update_auto_check_summary),
                )
                // 当前版本（静态行）
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.update_current_version),
                        style = MiuixTheme.textStyles.body1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "v${BuildConfig.VERSION_NAME}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            // 联网权限用途说明（INTERNET 为普通权限，安装即授，无需运行时申请）
            Text(
                text = stringResource(R.string.update_internet_note),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            // 自定义镜像前缀输入
            OverlayDialog(
                show = showCustomMirrorDialog,
                title = stringResource(R.string.custom_mirror_title),
                onDismissRequest = { showCustomMirrorDialog = false },
                ) {
                Column(Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.TextField(
                        value = customMirrorInput,
                        onValueChange = { customMirrorInput = it },
                        label = stringResource(R.string.custom_mirror_hint),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = { showCustomMirrorDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.action_confirm),
                            onClick = {
                                SettingsStore.setCustomMirror(customMirrorInput.trim())
                                showCustomMirrorDialog = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                }
            }

            // 检查结果对话框
            val r = state.result
            OverlayDialog(
                show = r != null,
                title = stringResource(R.string.about_check_update),
                onDismissRequest = { state.result = null },
            ) {
                UpdateResultContent(
                result = r,
                onDismiss = { state.result = null },
                onRetry = {
                    state.result = null
                    state.check()
                },
                onDownload = { url ->
                    openUrl(context, url)
                    state.result = null
                },
            )
        }
    }
}

// ==================== material 版 ====================

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun UpdateSettingsMaterial(onBack: () -> Unit) {
    val context = LocalContext.current
    val mirror by SettingsStore.updateMirror.collectAsState(initial = SettingsStore.cache.updateMirror)
    val autoCheck by SettingsStore.autoCheckUpdate.collectAsState(initial = SettingsStore.cache.autoCheckUpdate)
    var showMirrorDialog by remember { mutableStateOf(false) }
    val state = remember { UpdateCheckState(onFinished = {}) }

    androidx.compose.material3.Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { androidx.compose.material3.Text(stringResource(R.string.about_check_update)) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingCard(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(R.string.about_check_update),
                summary = if (state.checking) {
                    stringResource(R.string.update_checking)
                } else {
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME)
                },
                onClick = { state.check() },
                trailing = { },
            )
            SettingCard(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(R.string.update_open_release),
                summary = "https://github.com/Yizhou147/FrostHush/releases/latest",
                onClick = {
                    val r = UpdateChecker.lastResult
                    openUrl(
                        context,
                        (r as? UpdateChecker.CheckResult.Available)?.release?.htmlUrl
                            ?: "https://github.com/Yizhou147/FrostHush/releases/latest",
                    )
                },
                trailing = { },
            )
            SettingCard(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(R.string.update_mirror),
                summary = stringResource(UpdateChecker.UpdateMirror.fromId(mirror).labelRes),
                onClick = { showMirrorDialog = true },
                trailing = { },
            )
            SettingCard(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(R.string.update_auto_check),
                summary = stringResource(R.string.update_auto_check_summary),
                onClick = { SettingsStore.setAutoCheckUpdate(!autoCheck) },
                trailing = {
                    androidx.compose.material3.Switch(
                        checked = autoCheck,
                        onCheckedChange = { SettingsStore.setAutoCheckUpdate(it) },
                    )
                },
            )
            // 当前版本（静态行）
            SettingCard(
                icon = Icons.Filled.CloudDownload,
                title = stringResource(R.string.update_current_version),
                summary = "v${BuildConfig.VERSION_NAME}",
                onClick = {},
                trailing = {},
            )
            // 联网权限用途说明（INTERNET 为普通权限，安装即授，无需运行时申请）
            androidx.compose.material3.Text(
                text = stringResource(R.string.update_internet_note),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )

            val r = state.result
            if (r != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { state.result = null },
                    title = { androidx.compose.material3.Text(stringResource(R.string.about_check_update)) },
                    text = { UpdateResultContentMaterial(result = r) },
                    confirmButton = {
                        when (r) {
                            is UpdateChecker.CheckResult.Available -> androidx.compose.material3.TextButton(
                                onClick = {
                                    openUrl(context, r.release.apkDownloadUrl ?: r.release.htmlUrl)
                                    state.result = null
                                },
                            ) { androidx.compose.material3.Text(stringResource(R.string.update_download)) }
                            is UpdateChecker.CheckResult.Failed -> androidx.compose.material3.TextButton(
                                onClick = {
                                    state.result = null
                                    state.check()
                                },
                            ) { androidx.compose.material3.Text(stringResource(R.string.plan_rel_retry)) }
                            is UpdateChecker.CheckResult.UpToDate -> androidx.compose.material3.TextButton(
                                onClick = { state.result = null },
                            ) { androidx.compose.material3.Text(stringResource(R.string.action_confirm)) }
                        }
                    },
                    dismissButton = if (r !is UpdateChecker.CheckResult.UpToDate) {
                        {
                            androidx.compose.material3.TextButton(onClick = { state.result = null }) {
                                androidx.compose.material3.Text(stringResource(R.string.action_cancel))
                            }
                        }
                    } else {
                        null
                    },
                )
            }

            if (showMirrorDialog) {
                ChoiceDialog(
                    title = stringResource(R.string.update_mirror),
                    options = UpdateChecker.UpdateMirror.entries.map { stringResource(it.labelRes) },
                    selectedIndex = UpdateChecker.UpdateMirror.entries
                        .indexOfFirst { it.id == mirror }.coerceAtLeast(0),
                    onSelect = {
                        SettingsStore.setUpdateMirror(UpdateChecker.UpdateMirror.entries[it].id)
                        showMirrorDialog = false
                    },
                    onDismiss = { showMirrorDialog = false },
                )
            }
        }
    }
}

@Composable
private fun UpdateResultContentMaterial(result: UpdateChecker.CheckResult?) {
    when (result) {
        is UpdateChecker.CheckResult.Available -> {
            if (result.release.body.isNotBlank()) {
                Column(
                    Modifier
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(result.release.body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                }
            }
        }
        is UpdateChecker.CheckResult.UpToDate -> Text(stringResource(R.string.update_latest))
        is UpdateChecker.CheckResult.Failed -> Text(stringResource(R.string.update_failed))
        null -> {}
    }
}

// ==================== 共享内容（miuix 对话框内部） ====================

@Composable
private fun UpdateResultContent(
    result: UpdateChecker.CheckResult?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDownload: (String) -> Unit,
) {
    when (val r = result) {
        is UpdateChecker.CheckResult.Available -> {
            Text(
                text = stringResource(R.string.update_available_title, r.release.tagName),
                style = MiuixTheme.textStyles.title3,
            )
            if (r.release.body.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = r.release.body,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.update_download),
                    onClick = { onDownload(r.release.apkDownloadUrl ?: r.release.htmlUrl) },
                    modifier = Modifier.weight(1f),
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
        is UpdateChecker.CheckResult.UpToDate -> {
            Text(text = stringResource(R.string.update_latest), style = MiuixTheme.textStyles.body1)
            Spacer(Modifier.height(16.dp))
            TextButton(
                text = stringResource(R.string.action_confirm),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        is UpdateChecker.CheckResult.Failed -> {
            Text(text = stringResource(R.string.update_failed), style = MiuixTheme.textStyles.body1)
            if (r.message.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = r.message,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = stringResource(R.string.plan_rel_retry),
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        null -> {}
    }
}
