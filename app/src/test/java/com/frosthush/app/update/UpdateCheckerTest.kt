package com.frosthush.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** UpdateChecker 版本比较回归测试（按 "." 分段数值比较） */
class UpdateCheckerTest {

    @Test
    fun `patch 段数值比较不受位数影响`() {
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.10.0"))
    }

    @Test
    fun `相同版本不算有更新`() {
        assertFalse(UpdateChecker.isNewer("1.2.2", "1.2.2"))
    }

    @Test
    fun `缺失段按 0 补齐`() {
        assertTrue(UpdateChecker.isNewer("1.3", "1.2.9"))
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0"))
    }

    @Test
    fun `非数字版本段保守返回无更新`() {
        // 预发布 tag / 脏数据：宁可漏报也不误报（修复点：旧实现 remote != current 一律报有更新）
        assertFalse(UpdateChecker.isNewer("1.2.0-rc1", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("", "1.2.2"))
        assertFalse(UpdateChecker.isNewer("v1.3.0", "1.2.2"))
    }
}
