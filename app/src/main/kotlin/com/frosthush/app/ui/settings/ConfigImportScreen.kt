package com.frosthush.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.frosthush.app.data.FocusStore
import com.frosthush.app.data.FocusStore.FocusPlan
import com.frosthush.app.data.FocusStore.PlanImportAction
import com.frosthush.app.ui.theme.LocalUiMode
import com.frosthush.app.ui.theme.UiMode

/**
 * 配置导入预览页：展示解析后的 [FocusStore.ConfigData]（应用集 / 计划 / 预设），
 * 支持逐项自定义后确认导入，或整体覆盖导入。
 *
 * 双 UI：按当前界面风格分派到 miuix / material 两套实现（对齐 KernelSU 的页面组织方式）。
 * 逐项编辑状态供两套实现共用（同包内顶层类不能重名），故集中在分派器内。
 */
@Composable
fun ConfigImportScreen(data: FocusStore.ConfigData, onBack: () -> Unit) {
    when (LocalUiMode.current) {
        UiMode.Miuix -> ConfigImportScreenMiuix(data = data, onBack = onBack)
        UiMode.Material -> ConfigImportScreenMaterial(data = data, onBack = onBack)
    }
}

// ---------- 逐项编辑状态（material / miuix 两版共用） ----------

/** 应用集导入项：可重命名 / 可关闭不导入 */
class GroupImportState(val source: FocusStore.AppGroup, val wasDefault: Boolean) {
    var importEnabled by mutableStateOf(true)
    var name by mutableStateOf(source.name)
    /** 本机未安装、导入时将跳过的应用数（子页提示用） */
    var notInstalledCount by mutableStateOf(0)
}

/** 预设导入项：可重命名 / 可关闭不导入 */
internal class PresetImportState(val source: FocusStore.FocusPreset) {
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
