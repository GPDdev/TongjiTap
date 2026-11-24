# 已完成：循济 TongjiTap 1.0.1 本地开发

## 1. 当前目标
1.0.1 功能开发和本地验证已完成：中文四页去除英文副标题；三条可选择的互动教程；隐私政策读到底才能同意；简化 Star 文案；更多增加浏览器下载和可开关的更新提示。四语言 README 和截图已更新，保留用户原文。远程发行结果见 https://github.com/GPDdev/TongjiTap/releases/tag/v1.0.1 和本机 handoffs/2026-11-24-tongjitap-1.0.1.md。

## 2. 完成条件
44 项单元测试、Android 8 的 37 项和 Android 16 的 51 项设备场景通过，Lint 0 错误和警告。正式包冷安装及 1.0.0 → 1.0.1 覆盖升级通过，UID 和循环次数 13 保留。签名及 16 KB 对齐检查通过。证据与限制见 VALIDATION.md。

## 3. 已确认事实
应用 ID org.hachile.tongjitap，versionName 1.0.1，versionCode 5，复用正式签名。后续未另行指定时更新为 1.0.2/code 6。原点击、音乐和屏幕检测功能保留。教程使用内置截图和原创节拍，支持实际选点、表单与调速手势，演练不写真实配置或改变网络。自动更新默认关闭；开启后读取 GitHub 最新正式 Release，只有更新且含有效 APK 时提示，浏览器下载、手动安装。

## 4. 相关文件与命令
MainActivity 使用 ComponentActivity 的返回处理；GuidedTutorial、CoachOverlay、TutorialScreens 提供教程。UpdateChecker、ReleaseVersion 管理更新检测；AppPreferences 保存开关，assets/i18n 包含中英日德所需文案。保留 org.hachile.tongjitap、Apache-2.0 和原正式签名，不提交 .signing。

scripts/build-release.ps1 构建 dist/tongjitap-1.0.1.apk；scripts/check-device.py 提供 onboarding、formal、regression、integration 套件；scripts/smoke-release.py 检查正式安装与升级。证据在 dist/checks 下各系统的 1.0.1 子目录。发布 APK 固定名 tongjitap.apk；源码使用 git archive，禁止打包工作目录。开发分支 codex/tongjitap-1.0.1，目标 main，标签 v1.0.1。

## 5. 已否决方案
不使用纯文字翻页教程，不让练习改动真实坐标、曲库或手机网络；不自动安装 APK，不以字符串顺序比较版本，不弹出预览版或没有 APK 的发行版本。不改变原 ID、签名、许可证或用户删减后的 README 结构。

## 6. 风险与待确认项
无本地开发待办。尚未在真实手机、厂商系统、TJ-WIFI 和微信运动页面验证；本轮未验证在线音乐接口。调试设备场景与正式包安装检查分别记录，不能把模拟器结果等同于实际校园网表现。下载仍依赖用户设备连接 GitHub。

## 7. 下一步动作
无，本版本地开发已完成。后续修改另开版本；远程发布状态以发行页和本机版本交接文件为准。
