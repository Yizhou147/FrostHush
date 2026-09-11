package com.frosthush.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 配置导入 · miuix 版（HyperOS 设计语言）：
 * 第一页摘要 + 三个模块卡片（ArrowPreference，可点进子页逐项自定义），底部「开始导入」/「整体覆盖导入」；
 * 子页用卡片列表承载名称输入框 + 导入开关 / 冲突二选一；整体覆盖前用 OverlayDialog 二次确认。
 *
 * 业务逻辑与 material 版保持一致：分组 / 勾选状态、计划冲突二选一、导入函数调用、Toast、返回时机均不变。
 */
@Composable
fun ConfigImportScreenMiuix(data: ConfigData, onBack: () -> Unit) {
    val context = LocalContext.current
    val preview = remember(data) { FocusStore.previewConfigMerge(data) }
    val localPlanNames = remember { FocusStore.focusPlans().associate { it.id to it.name } }

    val groups = remember(preview) {
        mutableStateListOf<GroupImportState>().apply {
            preview.groups.forEach { g ->
                add(GroupImportState(g, g.isDefault).apply {
                    notInstalledCount = g.entries.size - FocusStore.filterInstalled(g.entries).size
                })
            }
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
            FocusStore.FocusPreset(it.source.id, it.name.trim().ifEmpty { it.source.name }, it.source.minutes, it.source.segments)
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
        // 被过滤的本机未安装应用单独提示
        if (r.appsFiltered > 0) {
            Toast.makeText(
                context,
                context.getString(R.string.config_import_apps_filtered, r.appsFiltered),
                Toast.LENGTH_LONG,
            ).show()
        }
        FocusManager.bumpVersion()
        PlanScheduler.scheduleAll(context)
        onBack()
    }

    fun doOverwriteImport() {
        val filtered = FocusStore.applyConfigOverwrite(data)
        Toast.makeText(context, context.getString(R.string.settings_import_success), Toast.LENGTH_SHORT).show()
        if (filtered > 0) {
            Toast.makeText(
                context,
                context.getString(R.string.config_import_apps_filtered, filtered),
                Toast.LENGTH_LONG,
            ).show()
        }
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
                showOverwriteConfirm = showOverwriteConfirm,
                onOpenGroups = { page = 1 },
                onOpenPlans = { page = 2 },
                onOpenPresets = { page = 3 },
                onStartImport = ::doMergeImport,
                onOverwriteImport = { showOverwriteConfirm = true },
                onDismissOverwrite = { showOverwriteConfirm = false },
                onConfirmOverwrite = {
                    showOverwriteConfirm = false
                    doOverwriteImport()
                },
                onBack = onBack,
            )
            1 -> GroupsImportPage(groups, onBack = { page = 0 })
            2 -> PlansImportPage(plans, onBack = { page = 0 })
            else -> PresetsImportPage(presets, onBack = { page = 0 })
        }
    }
}

/** 第一页：摘要 + 三个模块卡片 + 开始导入 / 整体覆盖导入 */
@Composable
private fun ConfigImportHome(
    data: ConfigData,
    groupsIn: Int, groupsTotal: Int,
    plansIn: Int, plansTotal: Int,
    presetsIn: Int, presetsTotal: Int,
    showOverwriteConfirm: Boolean,
    onOpenGroups: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenPresets: () -> Unit,
    onStartImport: () -> Unit,
    onOverwriteImport: () -> Unit,
    onDismissOverwrite: () -> Unit,
    onConfirmOverwrite: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.config_import_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 内容区可滚动（配合顶栏收起），导入按钮固定在底部
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.config_import_summary, data.groups.size, data.plans.size, data.presets.size),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                )
                Card {
                    ModuleCard(
                        icon = MiuixIcons.Folder,
                        title = stringResource(R.string.group_title),
                        summary = stringResource(R.string.config_import_module_summary, groupsTotal, groupsIn),
                        onClick = onOpenGroups,
                    )
                    ModuleCard(
                        icon = MiuixIcons.Months,
                        title = stringResource(R.string.plan_title),
                        summary = stringResource(R.string.config_import_module_summary, plansTotal, plansIn),
                        onClick = onOpenPlans,
                    )
                    ModuleCard(
                        icon = MiuixIcons.Timer,
                        title = stringResource(R.string.config_import_presets),
                        summary = stringResource(R.string.config_import_module_summary, presetsTotal, presetsIn),
                        onClick = onOpenPresets,
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Button(
                    onClick = onStartImport,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = groupsIn + plansIn + presetsIn > 0,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(stringResource(R.string.config_import_action))
                }
                TextButton(
                    text = stringResource(R.string.config_import_overwrite_action),
                    onClick = onOverwriteImport,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        color = Color.Transparent,
                        disabledColor = Color.Transparent,
                        textColor = MiuixTheme.colorScheme.error,
                        disabledTextColor = MiuixTheme.colorScheme.disabledOnSurface,
                    ),
                )
            }
        }
        // 整体覆盖导入二次确认（HyperOS 弹窗样式；OverlayDialog 需有 Scaffold 祖先才能渲染）
        OverlayDialog(
            show = showOverwriteConfirm,
            title = stringResource(R.string.config_import_overwrite_title),
            summary = stringResource(R.string.config_import_overwrite_warning),
            onDismissRequest = onDismissOverwrite,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = onDismissOverwrite,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = stringResource(R.string.config_import_overwrite_action),
                    onClick = onConfirmOverwrite,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

/** 模块卡片：图标 + 标题 + 摘要 + 末位箭头（对齐 miuix 跳转项） */
@Composable
private fun ModuleCard(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ArrowPreference(
        title = title,
        summary = summary,
        startAction = {
            Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceContainer)
        },
        onClick = onClick,
    )
}

/** 应用集子页：每行名称输入框（可编辑）+ 导入开关 + 应用数/原默认集/未安装提示 */
@Composable
private fun GroupsImportPage(groups: List<GroupImportState>, onBack: () -> Unit) {
    SubPageScaffold(title = stringResource(R.string.group_title), onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(groups) { _, g ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = g.name,
                                onValueChange = { g.name = it },
                                enabled = g.importEnabled,
                                label = stringResource(R.string.group_name),
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Switch(checked = g.importEnabled, onCheckedChange = { g.importEnabled = it })
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.group_items_count, g.source.entries.size),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (g.wasDefault) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.config_import_group_default_hint),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            if (g.notInstalledCount > 0) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.config_import_group_not_installed, g.notInstalledCount),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 计划子页：状态徽标 + 名称输入框 + 导入开关；冲突项就地二选一 */
@Composable
private fun PlansImportPage(plans: List<PlanImportState>, onBack: () -> Unit) {
    SubPageScaffold(title = stringResource(R.string.plan_title), onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(plans) { _, p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = p.name,
                                onValueChange = { p.name = it },
                                enabled = p.canEdit,
                                label = stringResource(R.string.plan_name),
                                useLabelAsPlaceholder = true,
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(12.dp))
                            when (p.action) {
                                PlanImportAction.ADD, PlanImportAction.RENAME ->
                                    Switch(checked = p.importEnabled, onCheckedChange = { p.importEnabled = it })
                                PlanImportAction.SKIP ->
                                    StatusBadge(stringResource(R.string.config_import_plan_skip), MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                PlanImportAction.CONFLICT -> Unit
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${timeRange(p.plan)} · ${weekdaySummary(p.plan.weekdays)}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.weight(1f),
                            )
                            if (p.action == PlanImportAction.RENAME) {
                                StatusBadge(stringResource(R.string.config_import_plan_rename), MiuixTheme.colorScheme.primary)
                            }
                        }
                        if (p.action == PlanImportAction.CONFLICT) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.config_import_plan_conflict),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            // 卡片内已有 16dp 内边距，这里去掉首选项自带的横向内边距避免双重缩进
                            RadioButtonPreference(
                                title = stringResource(R.string.config_import_keep_local, p.localName ?: ""),
                                selected = !p.conflictReplace,
                                onClick = { p.conflictReplace = false },
                                insideMargin = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            )
                            RadioButtonPreference(
                                title = stringResource(R.string.config_import_use_imported, p.plan.name),
                                selected = p.conflictReplace,
                                onClick = { p.conflictReplace = true },
                                insideMargin = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 预设子页：每行名称输入框 + 段序列/分钟数 + 导入开关 */
@Composable
private fun PresetsImportPage(presets: List<PresetImportState>, onBack: () -> Unit) {
    SubPageScaffold(title = stringResource(R.string.config_import_presets), onBack = onBack) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(presets) { _, pr ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextField(
                            value = pr.name,
                            onValueChange = { pr.name = it },
                            enabled = pr.importEnabled,
                            label = stringResource(R.string.focus_preset_name),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            // 分段预设显示段序列（如 25+5+25）；旧单段预设显示分钟数（兼容）
                            text = pr.source.sequenceText,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = pr.importEnabled, onCheckedChange = { pr.importEnabled = it })
                    }
                }
            }
        }
    }
}

/** 子页通用脚手架：TopAppBar + 返回 */
@Composable
private fun SubPageScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = title,
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
                .padding(padding)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            content()
        }
    }
}

@Composable
private fun StatusBadge(text: String, color: Color) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.Medium),
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
