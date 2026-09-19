package com.frosthush.app.ui.focus

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.frosthush.app.R
import com.frosthush.app.data.FocusStore
import com.frosthush.app.focus.FocusManager
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 专注环形倒计时（miuix 版，对齐 HyperOS 系统时钟计时页的观感）：
 * - 大圆环：蓝色弧（primary）= 本段剩余比例，随倒计时递减；轨道 secondaryContainer 浅灰
 * - 弧线平滑走动：倒计时每秒更新目标值，用 1s 线性补间连续逼近（实测逐秒跳变被用户反馈「一顿一顿」）；
 *   段切换/新会话（段起点变化）时比例从近 0 跳回近 1，直接落位不做回绕动画
 * - 环心：等宽倒计时（防数字抖动）+ 本段/整场说明；阶段标题与暂停应用数由调用方放在环外
 *   （真机对比后用户定稿：全放环内太挤，回到第一版布局）
 *
 * FocusLockScreenMiuix（专注段全屏页）与 FocusScreenMiuix 的休息内容共用，保证两处观感一致。
 * 环尺寸由调用方按可用空间传入（横屏/小屏收缩防溢出），时间字号与描边按环尺寸等比缩放。
 *
 * @param remaining          本段剩余毫秒（segmentEndMillis - now，负值已由调用方收敛为 0）
 * @param segmentStartMillis 本段开始时刻（毫秒）；作为段身份用于检测段切换（切换时弧线 snap 落位）
 * @param segmentEndMillis   本段结束时刻（毫秒）；弧长比例 = remaining / (end - start)
 * @param subLabel           环内倒计时下方的说明文案（如「共 30 分钟」）；空串则不显示
 * @param preferredRingSize  环外径；调用方已按屏幕可用宽高收缩
 */
@Composable
internal fun FocusRingMiuix(
    remaining: Long,
    segmentStartMillis: Long,
    segmentEndMillis: Long,
    subLabel: String,
    modifier: Modifier = Modifier,
    preferredRingSize: Dp = 280.dp,
) {
    // 弧长比例 = 本段剩余 / 本段总时长（与系统计时器同语义：蓝弧递减，走完一段切换下一段时重置）
    val progress = if (segmentEndMillis > segmentStartMillis) {
        (remaining.toFloat() / (segmentEndMillis - segmentStartMillis).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 段切换检测：段起点变化 = 进休息/回专注/新会话，此时 snap 落位；段内每秒 1s 线性补间平滑走动
    var lastSegmentStart by remember { mutableLongStateOf(segmentStartMillis) }
    val segmentSwitched = lastSegmentStart != segmentStartMillis
    SideEffect { lastSegmentStart = segmentStartMillis }
    val animProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = if (segmentSwitched) snap() else tween(durationMillis = 1000, easing = LinearEasing),
        label = "focusRingProgress",
    )
    // 时间字号/描边随环等比缩放：0.17 ≈ 280dp 环时 47.6sp（HH:MM:SS 八字符约 228dp，仍在环内径内）
    val timeFontSize = with(LocalDensity.current) { (preferredRingSize * 0.17f).toSp() }

    Box(modifier.size(preferredRingSize), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = animProgress,
            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                foregroundColor = MiuixTheme.colorScheme.primary,
                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
            ),
            strokeWidth = preferredRingSize * 0.03f,
            size = preferredRingSize,
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = FocusManager.countdownText(remaining),
                style = MiuixTheme.textStyles.title1.copy(fontSize = timeFontSize),
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            )
            if (subLabel.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subLabel,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

/**
 * 环内说明文案：
 * - 休息段：「休息 X 分钟」（当前休息段时长，跳过休息重写后的实际时长）
 * - 专注段（分段）：「第 N/M 段 · 共 X 分钟」（X = 整场总时长）
 * - 专注段（单段）：「共 X 分钟」（对齐系统计时器「共 5 分钟」）
 */
internal fun focusRingSubLabel(
    context: Context,
    isRest: Boolean,
    session: FocusStore.ActiveSession,
    phaseIndex: Int,
): String = when {
    isRest -> context.getString(
        R.string.focus_ring_rest_total,
        session.segmentMinutes.getOrElse(phaseIndex) { 0 },
    )
    session.isSegmented -> context.getString(
        R.string.focus_ring_segment_total,
        phaseIndex + 1,
        session.segmentMinutes.size,
        session.totalMinutes,
    )
    else -> context.getString(R.string.focus_ring_total, session.totalMinutes)
}
