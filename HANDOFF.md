# 已完成：循济 TongjiTap 0.2.0 本地开发

## 1. 当前目标
本版应用开发已完成：正式名称循济 / TongjiTap，应用 ID org.hachile.tongjitap，统一矢量图标、蓝色可操作和灰色禁用按钮、可配置颜色触发返回或点击。仓库地址为 https://github.com/GPDdev/TongjiTap。iOS 经用户决定暂缓，本轮不交付 iOS 安装包。

## 2. 完成条件
本地条件全部满足：19 项核心测试、Android 8 的 13 项与 Android 16 的 19 项设备检查通过；正式 APK 构建、Lint、v2 签名、对齐及两个系统冷安装通过。Apache-2.0 保留。具体证据和命令见 VALIDATION.md。

## 3. 已确认事实
原生 Java 17，AGP 8.13.2、Gradle 8.13，minSdk 26、targetSdk 36，versionCode 2。基础点击支持 Android 8，颜色检测使用 Android 11+ 的辅助功能截图。配置仅本地保存，不联网、不读页面节点、不保存截图。颜色动作在当前手势结束后执行一次，确认颜色离开后恢复步骤；持续命中、截图错误、全黑主体、停止或环境变化会终止后续调度。

## 4. 相关文件与命令
app 为正式应用；fixture 与 app/src/androidTest 为开发测试。ColorRule、ColorMonitor、SequenceRunner、TapService 负责匹配、内存截图、串行执行及控制面板。scripts/build-release.ps1 输出 dist/tongjitap-0.2.0.apk 和 SHA-256。README.md、README.en.md 为已获用户批准的下载和使用说明；docs/repository-settings.json 与 docs/release-notes-v0.2.0.md 记录发布内容。Git 发布操作及最终远程核验由本次本地 handoffs 文件单独记录；公开发布结果以仓库 Releases 为准。

## 5. 已否决方案
不以普通 iOS App 冒充能控制其他应用的自动点击器。不使用 OCR、页面节点、隐藏运行或自动恢复。保留历史提交和旧标签。全黑截图无法可靠区分遮罩与正常页面，采取保守停止，不把启发式识别宣传为确定的窗口检测。

## 6. 风险与待确认项
无本地开发待办。真实手机和微信未验证；坐标、颜色、容差需用户校准。新应用 ID 属于独立安装，旧配置不迁移。后续更新应递增 versionCode，私下备份并复用 .signing 中的正式签名，不得提交或共享密钥。SDK、dist、缓存和本机配置不进入 Git。

## 7. 下一步动作
无，本版本地开发已完成。后续功能以新的用户需求为准。
