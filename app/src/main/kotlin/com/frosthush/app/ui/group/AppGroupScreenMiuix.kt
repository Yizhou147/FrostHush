package com.frosthush.app.ui.group

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.frosthush.app.R
import com.frosthush.app.data.AppRepository
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.AppGroup
import com.frosthush.app.focus.FocusManager
import com.frosthush.app.ui.AppIcon
import com.frosthush.app.ui.AppSelectScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用集管理页 · miuix 版（HyperOS 设计语言）：
 * - 列表：应用集卡片（图标 + 集名/条目数/默认 badge），右上角进入多选
 * - 右上角 + 新建、选择键进入多选删除（复用专注页多选交互模式）
 * - 删除默认集后黑名单自动回退为空集；引用该集的计划回退到默认集
 * 业务逻辑与 material 版逐字一致，仅替换为 miuix 组件与 token。
 */
@Composable
fun AppGroupScreenMiuix(onBack: () -> Unit) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }
    // 应用集顺序：本地状态驱动列表，拖动排序时直接更新并持久化
    var groups by remember(refreshKey) { mutableStateOf(FocusStore.appGroups()) }
    val defaultId = remember(refreshKey) { FocusStore.defaultGroup()?.id }
    // 编辑页状态：editMode 区分列表/编辑，editTarget null 表示新建
    var editMode by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<AppGroup?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 被专注计划引用的应用集 id（删除时需确认，删除后计划回退默认集）
    val referencedGroupIds = remember(refreshKey) {
        FocusStore.focusPlans().mapNotNull { it.appGroupId }.toSet()
    }

    fun doDeleteSelected() {
        selected.forEach { FocusStore.deleteAppGroup(it) }
        // 删除默认集后提示回退为空集
        if (selected.contains(defaultId)) {
            Toast.makeText(
                context,
                context.getString(R.string.group_delete_default_fallback),
                Toast.LENGTH_SHORT,
            ).show()
        }
        FocusManager.bumpVersion()
        selected = emptySet()
        selectionMode = false
        refreshKey++
        confirmDelete = false
    }

    /** 拖拽排序：更新本地顺序并持久化（默认集不特殊置顶） */
    fun onReorder(fromIndex: Int, toIndex: Int) {
        if (fromIndex != toIndex) {
            groups = groups.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
            FocusStore.saveAppGroups(groups)
        }
    }

    // 列表 ↔ 编辑页淡入淡出过渡
    AnimatedContent(
        targetState = if (editMode) 1 else 0,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "groupEditTransition",
    ) { key ->
        if (key == 1) {
            // 编辑页内系统返回键：退回应用集列表
            BackHandler { editMode = false; editTarget = null }
            GroupEditScreenMiuix(
                group = editTarget,
                onBack = {
                    editMode = false
                    editTarget = null
                },
                onSaved = {
                    editMode = false
                    editTarget = null
                    refreshKey++
                },
            )
        } else {
            // 选择模式下系统返回键：只退出选择模式（与专注/计划页一致），不退出应用集页
            BackHandler(enabled = selectionMode) {
                selectionMode = false
                selected = emptySet()
            }
            GroupListContentMiuix(
                groups = groups,
                defaultId = defaultId,
                selectionMode = selectionMode,
                selected = selected,
                onReorder = ::onReorder,
                onSelectionModeChange = {
                    selectionMode = !selectionMode
                    if (!selectionMode) selected = emptySet()
                },
                onNew = {
                    editTarget = null
                    editMode = true
                },
                onEdit = {
                    editTarget = it
                    editMode = true
                },
                onToggleSelect = { id ->
                    selected = if (id in selected) selected - id else selected + id
                },
                onExitSelection = {
                    selectionMode = false
                    selected = emptySet()
                },
                onSelectAll = { ids -> selected = ids },
                onDeleteSelected = {
                    // 删除总是弹确认（被计划引用时文案额外提示回退默认集）
                    if (selected.isNotEmpty()) confirmDelete = true
                },
                onBack = onBack,
                confirmDelete = confirmDelete,
                hasReferenced = selected.any { it in referencedGroupIds },
                onDismissConfirmDelete = { confirmDelete = false },
                onConfirmDelete = { doDeleteSelected() },
            )
        }
    }
}

/** 应用集列表页（含多选操作栏 + 长按拖拽排序） */
@Composable
private fun GroupListContentMiuix(
    groups: List<AppGroup>,
    defaultId: Long?,
    selectionMode: Boolean,
    selected: Set<Long>,
    onReorder: (Int, Int) -> Unit,
    onSelectionModeChange: () -> Unit,
    onNew: () -> Unit,
    onEdit: (AppGroup) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: (Set<Long>) -> Unit,
    onDeleteSelected: () -> Unit,
    onBack: () -> Unit,
    confirmDelete: Boolean,
    hasReferenced: Boolean,
    onDismissConfirmDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = stringResource(R.string.group_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onSelectionModeChange) {
                        Icon(MiuixIcons.SelectAll, contentDescription = stringResource(R.string.focus_action_select))
                    }
                    IconButton(onClick = onNew) {
                        Icon(MiuixIcons.Add, contentDescription = stringResource(R.string.group_action_new))
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
            // 多选操作栏（复用专注页模式）
            AnimatedVisibility(
                visible = selectionMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onExitSelection) {
                        Icon(MiuixIcons.Close, contentDescription = stringResource(R.string.action_cancel))
                    }
                    Text(
                        text = stringResource(R.string.group_selected, selected.size),
                        style = MiuixTheme.textStyles.body1,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.focus_select_all),
                        onClick = { onSelectAll(groups.map { it.id }.toSet()) },
                    )
                    TextButton(
                        text = stringResource(R.string.focus_clear_selection),
                        onClick = { onSelectAll(emptySet()) },
                    )
                    IconButton(onClick = onDeleteSelected, enabled = selected.isNotEmpty()) {
                        Icon(
                            MiuixIcons.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
            }
            if (groups.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.group_empty),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // 排序交互：非多选模式长按行 → 进入多选；多选模式下长按某行 → 开始拖拽（该行放大并跟随手指，
                // 其余行通过 animateItem 平滑让位），拖动跨越半行即交换顺序并持久化
                val listState = rememberLazyListState()
                var draggingId by remember { mutableStateOf<Long?>(null) }
                var dragOffsetY by remember { mutableStateOf(0f) }
                var draggedHeightPx by remember { mutableStateOf(0f) }
                val latestGroups by rememberUpdatedState(groups)
                val latestOnReorder by rememberUpdatedState(onReorder)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                ) {
                    items(groups, key = { it.id }) { group ->
                        val isDragging = draggingId == group.id
                        val scale by animateFloatAsState(
                            targetValue = if (isDragging) 1.04f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessLow),
                            label = "groupDragScale",
                        )
                        Column(
                            modifier = (if (isDragging) Modifier else Modifier.animateItem())
                                // 被拖项禁用让位动画（仅跟手），避免 placement 动画与跟手位移叠加导致跳动；
                                // 其余项保留 animateItem 平滑让位
                                .padding(vertical = 4.dp)
                                .graphicsLayer {
                                    // 被拖项跟随手指；其余项保持原位由 animateItem 平滑让位
                                    translationY = if (isDragging) dragOffsetY else 0f
                                }
                                .zIndex(if (isDragging) 1f else 0f)
                                .scale(scale)
                                .onGloballyPositioned {
                                    if (isDragging) draggedHeightPx = it.size.height.toFloat()
                                }
                                .pointerInput(group.id, selectionMode) {
                                    if (!selectionMode) return@pointerInput
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingId = group.id
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggingId = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (draggingId != group.id) return@detectDragGesturesAfterLongPress
                                            dragOffsetY += amount.y
                                            val list = latestGroups
                                            val currentIndex = list.indexOfFirst { it.id == group.id }
                                            if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                            val h = draggedHeightPx.takeIf { it > 0f }
                                                ?: 72.dp.toPx()
                                            val targetIndex = (currentIndex + (dragOffsetY / h).roundToInt())
                                                .coerceIn(0, list.size - 1)
                                            if (targetIndex != currentIndex) {
                                                latestOnReorder(currentIndex, targetIndex)
                                                // 交换后补偿偏移，保证手指下的行不跳变
                                                dragOffsetY -= (targetIndex - currentIndex) * h
                                            }
                                        },
                                    )
                                },
                        ) {
                            GroupRowMiuix(
                                group = group,
                                isDefault = group.id == defaultId,
                                selectionMode = selectionMode,
                                selected = group.id in selected,
                                onClick = {
                                    if (selectionMode) onToggleSelect(group.id)
                                    else onEdit(group)
                                },
                                onLongClick = {
                                    if (!selectionMode) {
                                        onSelectionModeChange()
                                        onToggleSelect(group.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
            // 删除确认弹窗（miuix OverlayDialog，置于 Scaffold 内容内由 popup host 渲染）
            OverlayDialog(
                show = confirmDelete,
                title = stringResource(R.string.group_delete_confirm_title),
                summary = stringResource(
                    if (hasReferenced) R.string.group_delete_confirm_text
                    else R.string.group_delete_confirm_general
                ),
                onDismissRequest = onDismissConfirmDelete,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismissConfirmDelete,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_confirm),
                        onClick = onConfirmDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** 应用集列表项：miuix Card 内 Row（图标 + 名称/条目数/默认 badge + 多选复选框） */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupRowMiuix(
    group: AppGroup,
    isDefault: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        // 选中项用 tertiaryContainer 作为选中态容器（对齐 miuix 选中语义）
        colors = CardDefaults.defaultColors(
            color = if (selected) MiuixTheme.colorScheme.tertiaryContainer
            else MiuixTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                // 多选模式下不注册长按（长按留给拖拽排序，避免手势冲突）；非多选模式长按=进入多选
                .combinedClickable(onClick = onClick, onLongClick = if (selectionMode) null else onLongClick)
                .heightIn(min = 56.dp)
                .padding(vertical = 8.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MiuixIcons.Folder,
                    contentDescription = null,
                    tint = if (isDefault) MiuixTheme.colorScheme.primary
                    else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = group.name, style = MiuixTheme.textStyles.body1)
                    if (isDefault) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.group_default_badge),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.group_items_count, group.entries.size),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            // 右侧固定 48dp 槽位：仅多选模式显示复选框（非多选留空，行高一致）
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                if (selectionMode) {
                    Checkbox(
                        state = ToggleableState(selected),
                        onClick = { onClick() },
                    )
                }
            }
        }
    }
}

/** 新建 / 编辑应用集：名称输入 + 应用选择（复用 AppSelectScreen） */
@Composable
private fun GroupEditScreenMiuix(
    group: AppGroup?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val repo = remember { AppRepository(context) }
    var name by remember { mutableStateOf(group?.name ?: "") }
    var entries by remember { mutableStateOf(group?.entries ?: emptyList()) }
    // 设为默认：编辑页内开关（对齐计划页启用 Switch 的交互）
    var isDefault by remember { mutableStateOf(group?.isDefault == true) }
    var selecting by remember { mutableStateOf(false) }
    // 优先使用缓存的应用名称（内存/磁盘），避免名称加载慢而闪现包名
    val appNames = remember { mutableStateOf(AppRepository.cachedAppNames()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            // 直接查询全量（含分身）一次到位；不用不含分身的中间结果覆盖缓存，避免闪现裸包名
            val full = runCatching { repo.queryApps().associate { it.entry to it.displayName } }
                .getOrDefault(emptyMap())
            if (full.isNotEmpty()) {
                appNames.value = full
                AppRepository.updateAppNameCache(full)
            }
        }
    }

    fun save() {
        if (name.isBlank()) {
            Toast.makeText(context, context.getString(R.string.group_name_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (group == null) {
            // 新建应用集默认为非默认
            FocusStore.addAppGroup(name, entries)
        } else {
            // 新设为默认时先清除其他集的默认标记，再保存本集的名称/条目/默认标记
            if (isDefault && !group.isDefault) FocusStore.setDefaultGroup(group.id)
            FocusStore.updateAppGroup(group.copy(name = name.trim(), entries = entries, isDefault = isDefault))
        }
        FocusManager.bumpVersion()
        onSaved()
    }

    // 表单 ↔ 应用选择页淡入淡出过渡
    AnimatedContent(
        targetState = selecting,
        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
        label = "groupAppSelectTransition",
    ) { isSelecting ->
        if (isSelecting) {
            AppSelectScreen(
                initial = entries.toSet(),
                onBack = { selecting = false },
                onDone = {
                    entries = it.sorted()
                    selecting = false
                },
            )
        } else {
            val scrollBehavior = MiuixScrollBehavior()
            Scaffold(
                containerColor = MiuixTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = stringResource(if (group == null) R.string.group_action_new else R.string.group_action_edit),
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(MiuixIcons.Back, contentDescription = stringResource(R.string.action_cancel))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                    )
                },
                // 底部固定全宽「保存」主按钮，顶栏不再放次要文字按钮
                bottomBar = {
                    Button(
                        onClick = { save() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
            ) { padding ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = stringResource(R.string.group_name),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 设为默认开关（对齐计划页启用 Switch 交互）
                    Card {
                        SwitchPreference(
                            checked = isDefault,
                            onCheckedChange = { isDefault = it },
                            title = stringResource(R.string.group_set_default),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // 「选择应用」为次要动作：TextButton 而非全宽主按钮
                    TextButton(
                        text = stringResource(R.string.group_select_apps_count, entries.size),
                        onClick = { selecting = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (entries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.group_no_apps),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    } else {
                        Card {
                            entries.forEach { entry ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AppIcon(entry.substringBefore('@'), 32.dp)
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                        Text(
                                            text = appNames.value[entry] ?: entry,
                                            style = MiuixTheme.textStyles.body1,
                                            maxLines = 1,
                                        )
                                        Text(
                                            text = entry,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
