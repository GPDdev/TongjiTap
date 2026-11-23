# 已完成：循济 TongjiTap 0.2.1 本地开发

## 1. 当前目标
应用与文档的本地开发已完成：修正桌面图标缩放，统一中文字号，增加 QQ 群号 934139029 点击复制、底部 Powered by GPDdev 项目链接及本机截图选点取色。iOS 继续暂缓。GitHub 地址为 https://github.com/GPDdev/TongjiTap；最终远程发布核验记录在本机 handoffs/2026-11-23-tongjitap-0.2.1.md，公开安装包以 Releases 为准。

## 2. 完成条件
23 项核心测试、Android 8 的 15 项和 Android 16 的 24 项设备场景通过；Lint 无问题；正式 APK 签名、对齐和双系统冷安装通过。已用正式包验证 0.2.0 覆盖升级保留设置，以及真实系统文件选择器导入截图、选取原图坐标与 RGB 后保存。Apache-2.0 保留。证据见 VALIDATION.md。

## 3. 已确认事实
应用 ID org.hachile.tongjitap 不变，versionName 0.2.1、versionCode 3，复用正式签名和配置格式。基础点击需要 Android 8+，颜色功能需要 Android 11+。导入只设置检测位置和目标颜色，运行时仍读取实时屏幕像素；图片本机处理，不保存副本、不上传，没有新增网络或存储权限。只有匹配当前完整屏幕尺寸的图片可以导入，保存时重新检查屏幕和运行状态。

## 4. 相关文件与命令
MainActivity.java 与 Ui.java 管理主界面、群号、链接和字号；ScreenshotImport.java、ScreenshotPickerView.java、ScreenshotSelection.java 管理系统选图、解码、预览和坐标映射；ic_foreground.xml 修正自适应前景比例。AGENTS.md 记录默认只递增版本末位的规则。开发分支 codex/tongjitap-0.2.1，发布分支 main，版本标签 v0.2.1。

scripts/test-core.ps1、scripts/check-device.py、scripts/build-release.ps1 和 scripts/smoke-release.py 为复现入口。正式包位于 dist/tongjitap-0.2.1.apk；设备证据位于 dist/checks。tools/release-ui-check-0.2.1.py 是本机升级与系统选图核验脚本，不随源码发布。README.md、README.en.md、docs/repository-settings.json 和 docs/release-notes-v0.2.1.md 已同步版本、群号与功能说明。

## 5. 已否决方案
不按裁剪图或长截图猜测屏幕坐标；不把导入图片当作实时检测。不更换应用 ID 或正式签名，不改写历史提交和旧标签。不以普通 iOS App 宣称可以控制微信。全黑截图仍采取保守停止，不能据此识别所有受保护窗口。

## 6. 风险与待确认项
无本地开发待办。真实手机、微信、其他尺寸和厂商系统尚未实测。应选择两种页面颜色差异明显的位置，必要时调小容差。私下备份并复用 .signing 中的正式签名，禁止提交或共享密钥。SDK、dist、工具缓存和本机配置不进入 Git。

## 7. 下一步动作
无，本版本地开发已完成；后续需求另开版本，默认从 0.2.1 递增为 0.2.2。远程发布状态以本机发布交接文件和 GitHub Releases 为准。
