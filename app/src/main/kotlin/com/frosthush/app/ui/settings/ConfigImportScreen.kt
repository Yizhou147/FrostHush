package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.ConfigData
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.data.FocusStore.PlanImportAction
import com.frosthush.app.data.FocusStore.PlanMergeRequest
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.focus.PlanScheduler

// ---------- 逐项编辑状态 ----------

/** 应用集导入项：可重命名 / 可关闭不导入 */
class GroupImportState(val source: FocusStore.AppGroup, val wasDefault: Boolean) {
    var importEnabled by mutableStateOf(true)
    var name by mutableStateOf(source.name)
}

/** 预设导入项：可重命名 / 可关闭不导入 */
class PresetImportState(val source: FocusStore.FocusPreset) {
    var importEnabled by mutableStateOf(true)
    var name by mutableStateOf(source.name)
}

/** 计划导入项：ADD/RENAME 可重命名+开关；CONFLICT 二选一；SKIP 固定跳过 */
class PlanImportState(
    val plan: FocusPlan,
    val action: PlanImportAction,
    val conflictLocalId: Long?,
    val localName: String?,
    initialName: String,
) {
    var name by mutableStateOf(initialName)
    var importEnabled by mutableStateOf(true)
    /** CONFLICT：true=采用导入替换本地；false=保留本地 */
    var conflictReplace by mutableStateOf(false)
    /** 名称可编辑：跳过项不可编辑；新增/重命名项关闭导入后不可编辑 */
    val canEdit: Boolean
        get() = action != PlanImportAction.SKIP && (action == PlanImportAction.CONFLICT || importEnabled)
}

/**
 * 配置导入：第一页三个模块（应用集 / 计划 / 预设）均可点进子页逐项自定义
 * （自动重命名可手动修改、可关闭不导入；计划冲突项二选一），底部「开始导入」执行；
 * 另有「整体覆盖导入」入口（警告后整体替换）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigImportScreen(data: ConfigData, onBack: () -> Unit) {
    val context = LocalContext.current
    val preview = remember(data) { FocusStore.previewConfigMerge(data) }
    val localPlanNames = remember { FocusStore.focusPlans().associate { it.id to it.name } }

    val groups = remember(preview) {
        mutableStateListOf<GroupImportState>().apply {
            preview.groups.forEach { g -> add(GroupImportState(g, g.isDefault)) }
        }
    }
    val plans = remember(preview, localPlanNames) {
        mutableStateListOf<PlanImportState>().apply {
            preview.planItems.forEach { item ->
                add(PlanImportState(item.plan, item.action, item.conflictLocalId, localPlanNames[item.conflictLocalId], item.displayName))
            }
        }
    }
    val presets = remember(data) {
        mutableStateListOf<PresetImportState>().apply {
            data.presets.forEach { p -> add(PresetImportState(p)) }
        }
    }

    // 0 主页 1 应用集 2 计划 3 预设
    var page by remember { mutableStateOf(0) }
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    // 子页（二级菜单）时拦截系统返回退回首页；首页时放行（由外层退出导入页）
    BackHandler(enabled = page != 0) { page = 0 }

    // 各模块实际将导入的数量（驱动主页摘要与导入按钮）
    val groupsIn = groups.count { it.importEnabled }
    val plansIn = plans.count {
        when (it.action) {
            PlanImportAction.ADD, PlanImportAction.RENAME -> it.importEnabled
            PlanImportAction.CONFLICT -> it.conflictReplace
            PlanImportAction.SKIP -> false
        }
    }
    val presetsIn = presets.count { it.importEnabled }

    fun doMergeImport() {
        val groupReqs = groups.filter { it.importEnabled }.map {
            FocusStore.AppGroup(it.source.id, it.name.trim().ifEmpty { it.source.name }, it.source.entries, false)
        }
        val planReqs = plans.mapNotNull { st ->
            when (st.action) {
                PlanImportAction.SKIP -> null
                PlanImportAction.CONFLICT -> PlanMergeRequest(
                    st.plan, st.action, st.name.trim().ifEmpty { st.plan.name }, st.conflictLocalId, st.conflictReplace
                )
                PlanImportAction.ADD, PlanImportAction.RENAME ->
                    if (st.importEnabled) PlanMergeRequest(st.plan, st.action, st.name.trim().ifEmpty { st.plan.name }) else null
            }
        }
        val presetReqs = presets.filter { it.importEnabled }.map {
            FocusStore.FocusPreset(it.source.id, it.name.trim().ifEmpty { it.source.name }, it.source.minutes)
        }
        val r = FocusStore.applyConfigMerge(groupReqs, planReqs, presetReqs)
        Toast.makeText(
            context,
            context.getString(
                R.string.config_import_result_merge,
                r.groupsAdded,
                r.plansAdded + r.plansRenamed,
                r.plansSkipped + r.plansKeepLocal,
                r.presetsAdded,
            ),
            Toast.LENGTH_SHORT,
        ).show()
        FocusManager.bumpVersion()
        PlanScheduler.scheduleAll(context)
        onBack()
    }

    fun doOverwriteImport() {
        FocusStore.applyConfigOverwrite(data)
        Toast.makeText(context, context.getString(R.string.settings_import_success), Toast.LENGTH_SHORT).show()
        FocusManager.bumpVersion()
        PlanScheduler.scheduleAll(context)
        onBack()
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "configImportPage",
    ) { p ->
        when (p) {
            0 -> ConfigImportHome(
                data = data,
                groupsIn = groupsIn, groupsTotal = groups.size,
                plansIn = plansIn, plansTotal = plans.size,
                presetsIn = presetsIn, presetsTotal = presets.size,
                onOpenGroups = { page = 1 },
                onOpenPlans = { page = 2 },
                onOpenPresets = { page = 3 },
                onStartImport = ::doMergeImport,
                onOverwriteImport = { showOverwriteConfirm = true },
                onBack = onBack,
            )
            1 -> GroupsImportPage(groups, onBack = { page = 0 })
            2 -> PlansImportPage(plans, onBack = { page = 0 })
            else -> PresetsImportPage(presets, onBack = { page = 0 })
        }
    }

    if (showOverwriteConfirm) {
        AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text(stringResource(R.string.config_import_overwrite_title)) },
            text = { Text(stringResource(R.string.config_import_overwrite_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteConfirm = false
                    doOverwriteImport()
                }) { Text(stringResource(R.string.config_import_overwrite_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 第一页：摘要 + 三个模块卡片 + 开始导入 / 整体覆盖导入 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigImportHome(
    data: ConfigData,
    groupsIn: Int, groupsTotal: Int,
    plansIn: Int, plansTotal: Int,
    presetsIn: Int, presetsTotal: Int,
    onOpenGroups: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenPresets: () -> Unit,
    onStartImport: () -> Unit,
    onOverwriteImport: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.config_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                stringResource(R.string.config_import_summary, data.groups.size, data.plans.size, data.presets.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            ModuleCard(
                icon = Icons.Filled.Folder,
                title = stringResource(R.string.group_title),
                summary = stringResource(R.string.config_import_module_summary, groupsTotal, groupsIn),
                onClick = onOpenGroups,
            )
            ModuleCard(
                icon = Icons.Filled.CalendarMonth,
                title = stringResource(R.string.plan_title),
                summary = stringResource(R.string.config_import_module_summary, plansTotal, plansIn),
                onClick = onOpenPlans,
            )
            ModuleCard(
                icon = Icons.Filled.Timer,
                title = stringResource(R.string.config_import_presets),
                summary = stringResource(R.string.config_import_module_summary, presetsTotal, presetsIn),
                onClick = onOpenPresets,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onStartImport,
                modifier = Modifier.fillMaxWidth(),
                enabled = groupsIn + plansIn + presetsIn > 0,
            ) {
                Text(stringResource(R.string.config_import_action))
            }
            TextButton(
                onClick = onOverwriteImport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.config_import_overwrite_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 模块卡片：图标 + 标题 + 摘要（对齐设置页 SettingCard 风格） */
@Composable
private fun ModuleCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Spacer(Modifier.size(2.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 应用集子页：每行名称输入框（可编辑）+ 导入开关 + 应用数/原默认集提示 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupsImportPage(groups: List<GroupImportState>, onBack: () -> Unit) {
    SubPageScaffold(title = stringResource(R.string.group_title), onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            itemsIndexed(groups) { _, g ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = g.name,
                        onValueChange = { g.name = it },
                        enabled = g.importEnabled,
                        singleLine = true,
                        label = { Text(stringResource(R.string.group_name)) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = g.importEnabled, onCheckedChange = { g.importEnabled = it })
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.group_items_count, g.source.entries.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (g.wasDefault) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.config_import_group_default_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** 计划子页：状态徽标 + 名称输入框 + 导入开关；冲突项就地二选一 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlansImportPage(plans: List<PlanImportState>, onBack: () -> Unit) {
    SubPageScaffold(title = stringResource(R.string.plan_title), onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            itemsIndexed(plans) { _, p ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = p.name,
                            onValueChange = { p.name = it },
                            enabled = p.canEdit,
                            singleLine = true,
                            label = { Text(stringResource(R.string.plan_name)) },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        when (p.action) {
                            PlanImportAction.ADD, PlanImportAction.RENAME ->
                                Switch(checked = p.importEnabled, onCheckedChange = { p.importEnabled = it })
                            PlanImportAction.SKIP ->
                                StatusBadge(stringResource(R.string.config_import_plan_skip), MaterialTheme.colorScheme.onSurfaceVariant)
                            PlanImportAction.CONFLICT -> Unit
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${timeRange(p.plan)} · ${weekdaySummary(p.plan.weekdays)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (p.action == PlanImportAction.RENAME) {
                            StatusBadge(stringResource(R.string.config_import_plan_rename), MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    if (p.action == PlanImportAction.CONFLICT) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.config_import_plan_conflict),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RadioChoiceRow(
                            text = stringResource(R.string.config_import_keep_local, p.localName ?: ""),
                            selected = !p.conflictReplace,
                            onClick = { p.conflictReplace = false },
                        )
                        RadioChoiceRow(
                            text = stringResource(R.string.config_import_use_imported, p.plan.name),
                            selected = p.conflictReplace,
                            onClick = { p.conflictReplace = true },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

/** 预设子页：每行名称输入框 + 分钟数 + 导入开关 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetsImportPage(presets: List<PresetImportState>, onBack: () -> Unit) {
    SubPageScaffold(title = stringResource(R.string.config_import_presets), onBack = onBack) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            itemsIndexed(presets) { _, pr ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pr.name,
                        onValueChange = { pr.name = it },
                        enabled = pr.importEnabled,
                        singleLine = true,
                        label = { Text(stringResource(R.string.focus_preset_name)) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.stats_duration_minutes_only, pr.source.minutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = pr.importEnabled, onCheckedChange = { pr.importEnabled = it })
                }
                HorizontalDivider()
            }
        }
    }
}

/** 子页通用脚手架：TopAppBar + 返回 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubPageScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            content()
        }
    }
}

@Composable
private fun RadioChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
    )
}

/** 时间段文案：跨天显示「次日」，开始==结束显示「全天」（对齐计划页） */
@Composable
private fun timeRange(plan: FocusPlan): String = when {
    plan.endMinute > plan.startMinute -> stringResource(
        R.string.plan_time_range, timeText(plan.startMinute), timeText(plan.endMinute)
    )
    plan.endMinute < plan.startMinute -> stringResource(
        R.string.plan_time_range_cross, timeText(plan.startMinute), timeText(plan.endMinute)
    )
    else -> stringResource(R.string.plan_full_day)
}

private fun timeText(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)

/** 星期摘要：仅一次 / 每天 / 工作日 / 周末 / 周几列表 */
@Composable
private fun weekdaySummary(weekdays: Set<Int>): String {
    if (weekdays.isEmpty()) return stringResource(R.string.plan_once_only)
    val labels = listOf(
        stringResource(R.string.plan_weekday_mon),
        stringResource(R.string.plan_weekday_tue),
        stringResource(R.string.plan_weekday_wed),
        stringResource(R.string.plan_weekday_thu),
        stringResource(R.string.plan_weekday_fri),
        stringResource(R.string.plan_weekday_sat),
        stringResource(R.string.plan_weekday_sun),
    )
    return when {
        weekdays.size == 7 -> labels.joinToString("")
        weekdays == setOf(6, 7) -> stringResource(R.string.plan_weekend)
        weekdays.containsAll(setOf(1, 2, 3, 4, 5)) && weekdays.size == 5 -> stringResource(R.string.plan_workdays)
        else -> (1..7).filter { it in weekdays }.map { labels[it - 1] }.joinToString(" ")
    }
}
