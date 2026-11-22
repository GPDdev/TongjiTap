# 循济 TongjiTap 0.2.0 开发验证记录

日期：2026-11-22。以下为实际执行结果，使用专用 AOSP 模拟器及独立测试页面。详细记录面向开发者，普通用户请直接使用 README 中的下载入口。

## 构建与检查

| 项目 | 结果 |
| --- | --- |
| 核心单元测试 | 19 项通过，0 失败、0 跳过；执行状态机 15 项，颜色模型 4 项 |
| Android Lint | 0 错误、1 提示：固定的 Gradle 8.13 有较新版本可用；未为此次功能更新工具链 |
| Android 8 / API 26 | 13 项设备场景通过 |
| Android 16 / API 36 | 19 项设备场景通过 |
| 正式 APK | tongjitap-0.2.0.apk；org.hachile.tongjitap；versionCode 2；versionName 0.2.0 |
| 签名与对齐 | RSA 3072 位，APK v2 签名与 zipalign 验证通过 |
| 正式包安装 | 两个系统均通过冷安装、主动勾选说明及首页启动 |
| 资源与文档 | 新名称、包名、自适应和主题图标检查通过；中英文代码块、下载入口、本地链接及 XML/SVG 检查通过 |
| 权限声明 | 无 uses-permission；辅助功能服务使用系统绑定保护，API 30+ 声明截图能力 |

## 设备场景

两个模拟器使用 1080 × 1920 屏幕、420 dpi、x86_64 AOSP 镜像。仪器测试通过 UiAutomation 的 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES 模式与正式服务并行，独立 fixture 记录真实触摸事件。

两个系统共同通过 12 项：首次主动确认；配置持久化及损坏数据拒绝；手动编辑校验；首尾排序按钮禁用与正式身份；服务连接和取点不穿透；跨应用 A/B/A/B 顺序、短按、长按及释放后等待；倒计时停止；等待期间停止；面板遮挡保护；旋转停止和旧坐标拒绝；熄屏停止；撤权及重连不自动运行。

Android 8 另验证颜色功能不可用时呈灰色。Android 16 另通过 7 项：

1. 检测点和动作点保存，不新增普通点击步骤，不穿透操作。
2. 颜色规则编辑校验与持久化。
3. 实际截图像素命中后仅执行一次系统返回，恢复后继续下一步。
4. 实际截图像素命中后仅点击一次恢复位置，恢复后继续下一步。
5. 目标颜色持续存在时停止，不连续返回。
6. 在颜色恢复期间停止后，不再执行动作或下一步。
7. 受保护测试窗口产生黑色遮罩时停止，不把遮罩作为颜色条件执行动作。

返回与点击恢复的真实日志均只出现一条 RECOVERY 记录，然后收到下一步 B 的 down/up/click。时序测试仍使用 A 按住 50 ms、等待 300 ms，B 按住 700 ms、等待 350 ms，执行两轮。原始日志与截图在本机忽略目录 dist/checks/TongjiTap26、dist/checks/TongjiTap36；签名包冷安装证据在 dist/checks/Release26、dist/checks/Release36。

## 已知边界

- 尚未测试真实手机、微信小程序、厂商后台策略、其他辅助功能共存、分屏或其他屏幕尺寸。不能据此保证第三方记录有效。
- 颜色接口从 Android 11 提供，本轮实际颜色设备测试为 Android 16。基础点击设备测试包含 Android 8。
- 全屏截图接口可能对受保护内容返回黑色遮罩而非错误。程序在主体采样区域全黑时保守停止，不宣称能识别所有受保护窗口；正常全黑页面也会停止，不能用于这种场景。
- 颜色规则比较像素，不能识别业务页面。白色闪屏和相似背景可能误触发，容差和检测位置需要校准。
- 停止取消后续调度，不能保证撤回已提交给系统的手势。截图只在内存中采样并释放，没有文件保存、上传或文字识别。
- 设备场景运行调试构建；签名发布包另外完成安装与启动检查，两者使用相同正式功能源码且不混淆。

## 复现命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-core.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle.ps1 :app:assembleDebug :app:assembleDebugAndroidTest :fixture:assembleDebug
python scripts/setup-emulators.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-emulator.ps1 -Api 26
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-emulator.ps1 -Api 36
# 等待两个专用模拟器启动后执行：
python scripts/check-device.py --serial emulator-5556
python scripts/check-device.py --serial emulator-5558
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-release.ps1
python scripts/smoke-release.py
```

设备脚本只允许本项目 TongjiTap26 / TongjiTap36 模拟器，会重设测试应用和系统开关。不要对日常设备运行。生产 APK 不包含 fixture 或仪器测试代码。
