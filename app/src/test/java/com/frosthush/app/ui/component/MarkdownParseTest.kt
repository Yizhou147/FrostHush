package com.frosthush.app.ui.component

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新日志 Markdown 解析回归测试：
 * 发布说明取自 GitHub Release 正文，解析器只覆盖标题/列表/行内加粗与代码/链接这些实际用到的子集。
 */
class MarkdownParseTest {

    private val styles = MdInlineStyles(
        bold = SpanStyle(fontWeight = FontWeight.SemiBold),
        code = SpanStyle(),
        link = SpanStyle(),
    )

    private fun parse(md: String) = parseMarkdownBlocks(md, styles)

    @Test
    fun `标题与正文分块`() {
        val blocks = parse("## v1.3.1 更新\n\n**新增：快速专注**")
        assertEquals(2, blocks.size)
        assertEquals(MdKind.H2, blocks[0].kind)
        assertEquals("v1.3.1 更新", blocks[0].text.text)
        assertEquals(MdKind.PARAGRAPH, blocks[1].kind)
        // 加粗标记被解析掉，正文只剩文字
        assertEquals("新增：快速专注", blocks[1].text.text)
        assertTrue(blocks[1].text.spanStyles.isNotEmpty())
    }

    @Test
    fun `列表与两级缩进`() {
        val blocks = parse("- 一级\n  - 二级\n\n* 星号也是列表")
        assertEquals(3, blocks.size)
        assertEquals(MdKind.BULLET to 0, blocks[0].kind to blocks[0].indent)
        assertEquals(MdKind.BULLET to 1, blocks[1].kind to blocks[1].indent)
        assertEquals(MdKind.BULLET to 0, blocks[2].kind to blocks[2].indent)
        assertEquals("一级", blocks[0].text.text)
    }

    @Test
    fun `行内代码与链接只渲染文字`() {
        val blocks = parse("用 `setExact` 不行，见 [发布页](https://example.com/a)")
        assertEquals(1, blocks.size)
        val plain = blocks[0].text.text
        assertEquals("用 setExact 不行，见 发布页", plain)
        assertEquals(2, blocks[0].text.spanStyles.size)
    }

    @Test
    fun `空行与未闭合标记不崩`() {
        val blocks = parse("\n\n## 标题\n\n- **没闭合的加粗\n- 正常")
        assertEquals(3, blocks.size)
        assertEquals(MdKind.H2, blocks[0].kind)
        assertTrue(blocks[1].text.text.contains("没闭合的加粗"))
    }
}
