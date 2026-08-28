package com.frosthush.app.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.FocusManager
import kotlinx.coroutines.delay

/**
 * 专注模式的全屏锁定倒计时界面（与雹专注模式一致）：
 * 不透明背景遮挡下层内容、消费所有触摸、拦截返回键，不可打断。
 * 分段专注时仅在专注段显示（休息段由 AppRoot 隐藏锁屏）；休息段到点自动恢复下一段专注。
 * 倒计时以 FocusService 发布的 phase 为唯一数据源（与超级岛同源），每秒刷新剩余时间；
 * 会话结束（phase 变 null / 会话被清理）时由 AppRoot 或本界面兜底解除锁定。
 */
@Composable
fun FocusLockScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    // 阶段状态统一以 FocusService 发布的 phase 为准（与超级岛倒计时同源）：
    // FocusService 每秒更新，StateFlow 变化自动触发重组刷新，锁屏不会再因自身循环
    // 在进程冻结/墙钟跳变时被定格在旧值（如 00:00）；会话结束 phase 变 null 时由
    // AppRoot 解除锁屏，本界面仅负责展示剩余时间，阶段推进/结束交给 FocusService。
    val phase by FocusManager.phase.collectAsState()
    var remaining by remember { mutableLongStateOf(0L) }
    var pausedCount by remember { mutableIntStateOf(0) }
    val isRest = phase?.isFocus == false

    // 每秒依据最新阶段刷新剩余时间与暂停应用数；会话被清理（服务已结束）时退出锁屏
    LaunchedEffect(Unit) {
        while (true) {
            val session = FocusStore.activeSession() ?: break
            val current = FocusManager.phase.value
            remaining = current?.remainingAt(System.currentTimeMillis()) ?: 0L
            pausedCount = session.packages.size
            delay(1000L)
        }
        onFinished()
    }

    // 专注期间拦截返回键，禁止退出
    BackHandler { }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 不透明背景遮住下层内容
            .background(MaterialTheme.colorScheme.background)
            // 消费所有触摸事件，阻止点击穿透到下层页面
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(if (isRest) R.string.focus_rest_title else R.string.focus_lock_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = FocusManager.countdownText(remaining),
            style = MaterialTheme.typography.displayLarge,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = context.resources.getQuantityString(R.plurals.focus_apps_paused, pausedCount, pausedCount),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
