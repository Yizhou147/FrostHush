# 霜息 FrostHush

> 一款专注模式安卓应用：通过 Shizuku 暂停你选定的应用，让你保持专注。
>
> FrostHush is a standalone focus mode Android app. It suspends the apps you select via Shizuku, so you can stay focused.

[![Android CI](https://github.com/Yizhou147/FrostHush/actions/workflows/android.yml/badge.svg)](https://github.com/Yizhou147/FrostHush/actions/workflows/android.yml)

## 简介

「霜息 FrostHush」从开源项目 [Hail](https://github.com/aistra0528/Hail) 的**专注模式**功能独立而来，聚焦于一件事：**不可打断的专注会话**。

- 不包含 Hail 的冻结 / 停止等其它功能
- 纯本地运行，零联网，无任何遥测

## 功能特性

- **首次启动引导**：通知权限、读取已安装应用、Shizuku 权限三项引导授权，随时返回检查
- **手动导入**：按名称 / 包名 / 拼音搜索，分类筛选，全选 / 清空，中文按拼音排序
- **剪贴板导入**：粘贴包名列表（空格 / 逗号 / 换行分隔）批量导入
- **开始专注**：选择时长（15 / 30 / 45 / 60 / 90 / 120 分钟）→ 确认「不可中途退出」→ 立即暂停所选应用
- **不可打断**：会话期间无任何退出入口，倒计时前台服务持续运行
- **自动恢复**：设备重启、应用进程被杀后自动恢复会话并继续倒计时
- **专注统计**：今日 / 本周 / 本月 / 本年聚合、累计 / 日均 / 最长单次、近 7 / 30 天柱状图、会话明细
- **设置**：默认专注时长、专注结束通知开关
- **中英双语**：全部文案通过 `strings.xml` 提供

## 环境要求

| 项目 | 要求 |
|---|---|
| Android | 6.0（API 23）及以上 |
| [Shizuku](https://github.com/RikkaApps/Shizuku) | 需要已安装并授权（通过 ADB 或 root 启动） |
| 权限 | 通知权限（Android 13+ 运行时）、读取已安装应用、Shizuku |

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

本项目基于 [Hail](https://github.com/aistra0528/Hail)（GPL-3.0）的专注模式功能独立开发，仅提取专注相关逻辑，不含冻结 / 停止等功能。

## 隐私说明

所有数据均保存在本机，不会联网上传。

## License

GPL-3.0
