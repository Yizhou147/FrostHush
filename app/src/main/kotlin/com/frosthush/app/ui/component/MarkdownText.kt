package com.frosthush.app.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 更新日志的轻量 Markdown 渲染。
 *
 * 背景：更新说明直接取自 GitHub Release 正文（Markdown），此前用普通 Text 展示会把
 * `##`、`**`、`-` 等原样画出来。这里只实现发布说明实际用到的子集，不引入第三方 Markdown 库：
 * - 标题 `##` / `###`
 * - 列表 `-` / `*`（支持二级缩进）
 * - 行内 `**加粗**`、`` `代码` ``、`[文字](链接)`（链接只渲染文字并加下划线，避免在对话框里跳转）
 *
 * 解析是纯函数（[parseMarkdownBlocks]），渲染用 [BasicText] + 传入的样式，因此在
 * material / miuix 两套 UI 里都只依赖调用方给的 TextStyle 与颜色。
 */
internal enum class MdKind { H2, H3, BULLET, PARAGRAPH }

internal data class MdBlock(val kind: MdKind, val indent: Int, val text: AnnotatedString)

/** 行内样式（由调用方按主题色传入，保证两套 UI 各自跟随自己的配色） */
internal data class MdInlineStyles(val bold: SpanStyle, val code: SpanStyle, val link: SpanStyle)

/** 把 Markdown 文本解析成块序列（纯函数，便于单测） */
internal fun parseMarkdownBlocks(markdown: String, styles: MdInlineStyles): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    markdown.lines().forEach { raw ->
        val line = raw.trimEnd()
        if (line.isBlank()) return@forEach
        // 每两个空格算一级缩进（发布说明里最多两级）
        val indent = (line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') 2 else 1 } / 2)
            .coerceIn(0, 2)
        val body = line.trimStart()
        when {
            body.startsWith("### ") -> blocks += MdBlock(MdKind.H3, indent, inline(body.removePrefix("### "), styles))
            body.startsWith("## ") -> blocks += MdBlock(MdKind.H2, indent, inline(body.removePrefix("## "), styles))
            body.startsWith("# ") -> blocks += MdBlock(MdKind.H2, indent, inline(body.removePrefix("# "), styles))
            body.startsWith("- ") || body.startsWith("* ") || body.startsWith("+ ") ->
                blocks += MdBlock(MdKind.BULLET, indent, inline(body.drop(2), styles))
            else -> blocks += MdBlock(MdKind.PARAGRAPH, indent, inline(body, styles))
        }
    }
    return blocks
}

/** 行内解析：**加粗**、`代码`、[文字](链接)；其余按普通文本 */
private fun inline(text: String, styles: MdInlineStyles): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val bold = text.indexOf("**", i)
        val code = text.indexOf('`', i)
        val link = text.indexOf('[', i)
        val next = listOf(bold, code, link).filter { it >= 0 }.minOrNull()
        if (next == null) {
            append(text.substring(i))
            break
        }
        if (next > i) append(text.substring(i, next))
        when (next) {
            bold -> {
                val end = text.indexOf("**", bold + 2)
                if (end < 0) {
                    append(text.substring(bold))
                    i = text.length
                } else {
                    withStyle(styles.bold) { append(text.substring(bold + 2, end)) }
                    i = end + 2
                }
            }
            code -> {
                val end = text.indexOf('`', code + 1)
                if (end < 0) {
                    append(text.substring(code))
                    i = text.length
                } else {
                    withStyle(styles.code) { append(text.substring(code + 1, end)) }
                    i = end + 1
                }
            }
            else -> {
                val close = text.indexOf(']', link + 1)
                val open = if (close >= 0) text.indexOf('(', close) else -1
                val end = if (open >= 0) text.indexOf(')', open) else -1
                if (close < 0 || open != close + 1 || end < 0) {
                    append(text.substring(link))
                    i = text.length
                } else {
                    withStyle(styles.link) { append(text.substring(link + 1, close)) }
                    i = end + 1
                }
            }
        }
    }
}

/**
 * 渲染 Markdown 更新日志：标题加粗用 [headingColor]，正文用 [color]，列表带 `•` 前缀，
 * 二级缩进每级 12dp；块间距按类型给（标题更大）。
 */
@Composable
internal fun MarkdownText(
    markdown: String,
    style: TextStyle,
    color: Color,
    headingColor: Color,
    modifier: Modifier = Modifier,
) {
    val styles = MdInlineStyles(
        bold = SpanStyle(fontWeight = FontWeight.SemiBold),
        code = SpanStyle(fontFamily = FontFamily.Monospace),
        link = SpanStyle(color = headingColor, textDecoration = TextDecoration.Underline),
    )
    val blocks = remember(markdown, styles) { parseMarkdownBlocks(markdown, styles) }
    Column(modifier) {
        blocks.forEach { block ->
            val headline = block.kind == MdKind.H2 || block.kind == MdKind.H3
            val text = if (block.kind == MdKind.BULLET) {
                buildAnnotatedString { append(if (block.indent == 0) "• " else "◦ "); append(block.text) }
            } else {
                block.text
            }
            BasicText(
                text = text,
                style = style.merge(
                    TextStyle(
                        color = if (headline) headingColor else color,
                        fontWeight = when (block.kind) {
                            MdKind.H2 -> FontWeight.Bold
                            MdKind.H3 -> FontWeight.SemiBold
                            else -> null
                        },
                    )
                ),
                modifier = Modifier.padding(
                    start = (block.indent * 12).dp,
                    top = when (block.kind) {
                        MdKind.H2 -> 10.dp
                        MdKind.H3 -> 8.dp
                        else -> 2.dp
                    },
                    bottom = if (block.kind == MdKind.PARAGRAPH) 2.dp else 1.dp,
                ),
            )
        }
    }
}
