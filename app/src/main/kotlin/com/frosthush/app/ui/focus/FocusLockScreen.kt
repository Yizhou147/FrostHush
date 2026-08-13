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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 专注模式的全屏锁定倒计时界面（与雹专注模式一致）：
 * 不透明背景遮挡下层内容、消费所有触摸、拦截返回键，不可打断。
 * 分段专注时仅在专注段显示（休息段由 AppRoot 隐藏锁屏）；休息段到点自动恢复下一段专注，
 * 最后一段专注到点后按快照恢复并回调解除锁定。每秒刷新剩余时间。
 */
@Composable
fun FocusLockScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    var remaining by remember { mutableLongStateOf(0L) }
    var pausedCount by remember { mutableIntStateOf(0) }
    var isRest by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val session = FocusStore.activeSession() ?: break
            val phase = session.phaseAt(System.currentTimeMillis())
            remaining = phase.remainingAt(System.currentTimeMillis())
            isRest = phase.type == FocusStore.SEGMENT_REST
            pausedCount = session.packages.size
            if (phase.isFocus && remaining <= 0) {
                // 最后一段专注到点：后台恢复并结束会话
                withContext(Dispatchers.IO) { FocusManager.restoreAndEnd() }
                break
            }
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
