# 霜息 FrostHush

> 一款专注模式安卓应用：通过 Shizuku 暂停你选定的应用，让你保持专注。
>
> FrostHush is a standalone focus mode Android app. It suspends the apps you select via Shizuku, so you can stay focused.

[![Android CI](https://github.com/Yizhou147/FrostHush/actions/workflows/android.yml/badge.svg)](https://github.com/Yizhou147/FrostHush/actions/workflows/android.yml)

## 简介

霜息（FrostHush）起源于雹（[Hail](https://github.com/aistra0528/Hail)）：专注模式最初是在雹的基础上以附加功能的形式开发（[Yizhou147/Hail](https://github.com/Yizhou147/Hail)），随后被独立为专一的专注应用，聚焦于一件事：**不可打断的专注会话**。

- 不包含 Hail 的冻结 / 停止等其它功能
- 纯本地运行，零联网，无任何遥测

## 功能特性

- **首次启动引导**：通知权限、读取已安装应用、Shizuku 权限三项引导授权，随时返回检查
- **手动导入**：按名称 / 包名 / 拼音搜索，分类筛选，全选 / 清空，中文按拼音排序
- **剪贴板导入**：粘贴包名列表（空格 / 逗号 / 换行分隔）批量导入
- **应用分身**：支持小米双开等分身（双开空间）应用，可单独选择 / 暂停，名称带「· 分身」标识
- **应用集**：黑名单升级为可管理的应用集，支持新建 / 编辑 / 删除 / 设为默认；专注页横向 chips 一键切换集合
- **手动排序**：应用集与专注计划列表均可长按拖动排序，其余行平滑让位
- **专注计划**：定时自动进入 / 退出专注，支持跨天、不重复（执行一次即停用）、精确闹钟（15 秒前提醒）、与手动专注冲突时 5 分钟决策窗口
- **开始专注**：选择时长（15 / 30 / 45 / 60 / 90 / 120 分钟）→ 确认「不可中途退出」→ 立即暂停所选应用
- **不可打断**：会话期间无任何退出入口，倒计时前台服务持续运行
- **自动恢复**：设备重启、应用进程被杀后自动恢复会话并继续倒计时
- **专注统计**：今日 / 本周 / 本月 / 本年聚合、累计 / 日均 / 最长单次、近 7 / 30 天柱状图、会话明细
- **设置**：默认专注时长、专注结束通知、超级岛开关、主题模式（浅色 / 深色 / 跟随系统）、开始前二次确认
- **数据管理**：导出专注统计、导出 / 导入应用配置（应用集 / 专注计划 / 时长预设）、清空统计
- **小窗 / 横屏**：小窗模式底栏沉浸适配；横屏下左侧导航栏（NavigationRail）
- **中英双语**：全部文案通过 `strings.xml` 提供

## 环境要求

| 项目                                              | 要求                                    |
| ----------------------------------------------- | ------------------------------------- |
| Android                                         | 6.0（API 23）及以上                        |
| [Shizuku](https://github.com/RikkaApps/Shizuku) | 需要已安装并授权（通过 ADB 或 root 启动）            |
| 权限                                              | 通知权限（Android 13+ 运行时）、读取已安装应用、Shizuku |

## 构建

### 云编译（推荐）

推送到 `main` 分支即触发 [GitHub Actions](https://github.com/Yizhou147/FrostHush/actions) 自动构建，产物为 `FrostHush-release` artifact 中的 APK。

如需固定的 release 签名（可覆盖安装升级），请在仓库 Secrets 中配置：

- `KEYSTORE`：keystore 文件的 base64 内容
- `KEYSTORE_PASSWORD`：store 密码
- `KEYSTORE_ALIAS`：别名
- `KEYSTORE_ALIAS_PASSWORD`：key 密码

未配置时自动回退 debug 签名（每次构建签名不同，无法覆盖安装）。

### 本地构建

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/`。

## 工作原理

应用通过 Shizuku 调用系统隐藏 API `IPackageManager.setPackagesSuspendedAsUser` 暂停所选应用（Android 10+ 同时通过 `SuspendDialogInfo` 定制暂停弹窗文案）。会话信息持久化到本地文件，由前台服务倒计时，设备重启 / 进程被杀后自动恢复。所有数据仅保存在本机。

## 开源声明

雹（[Hail](https://github.com/aistra0528/Hail)，GPL-3.0）本体不含专注模式；专注模式最初是在雹的基础上额外添加的功能（见 [Yizhou147/Hail](https://github.com/Yizhou147/Hail)）。本项目将该功能独立为完整的专注应用，不含冻结 / 停止等雹的本体功能，专注相关逻辑参考雹的自写实现。

## 隐私说明

所有数据均保存在本机，不会联网上传。

## License

GPL-3.0
