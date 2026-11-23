# 循济 TongjiTap 0.2.1 开发验证记录

日期：2026-11-23。使用本项目专用 AOSP 模拟器与独立测试页面。普通用户请直接使用 README 的下载入口。

## 构建与检查

| 项目 | 结果 |
| --- | --- |
| 核心单元测试 | 23 项通过，0 失败、0 跳过；执行状态机 15 项、颜色模型 4 项、截图坐标映射 4 项 |
| Android Lint | 0 错误、0 警告 |
| Android 8 / API 26 | 15 项设备场景通过 |
| Android 16 / API 36 | 24 项设备场景通过 |
| 正式 APK | tongjitap-0.2.1.apk；org.hachile.tongjitap；versionCode 3；versionName 0.2.1 |
| 签名与对齐 | RSA 3072 位，APK v2 签名与 zipalign 验证通过；复用正式签名 |
| 正式包安装 | Android 8 和 Android 16 均通过冷安装、主动勾选说明及首页启动 |
| 覆盖更新 | 0.2.0 → 0.2.1 通过；签名证书一致，循环次数、点击坐标/时长/等待、颜色启用状态与全部参数保留 |
| 系统选图 | 通过真实系统文件选择器读取本机截图，选取原图坐标与 RGB 后成功保存；无需存储权限 |
| 资源与文档 | 原生绘制的桌面图标与正式图标比例一致；中英文下载入口、群号、代码块、本地链接和 XML/SVG 检查通过 |
| 权限声明 | 无 uses-permission；没有新增存储或网络权限；API 30+ 的辅助功能服务声明截图能力 |

## 设备场景

两个模拟器使用 1080 × 1920 屏幕、420 dpi、x86_64 AOSP 镜像。仪器测试通过 UiAutomation 的 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES 模式与正式服务并行，独立 fixture 记录真实触摸事件。

原有 32 项场景继续通过：主动确认、配置持久化与坏数据保护、编辑校验、排序禁用、取点不穿透、跨应用点击顺序与长按时序、倒计时和等待期间停止、面板遮挡、旋转和熄屏停止、撤权与重连。Android 8 的颜色入口与截图导入呈灰色；Android 16 继续覆盖颜色点/动作点、规则校验、截图触发返回和点击、持续命中停止、恢复期间手动停止及受保护窗口黑色遮罩停止。

新增 7 项设备场景：

- 两个系统分别验证 QQ 群号复制值恰为 934139029，底部链接发出指向本项目的 ACTION_VIEW。
- 两个系统分别用 Android 原生 Drawable 绘制比较图标比例，并检查首页和弹窗实际字号只有 30sp、20sp、16sp。
- Android 16 经真实跨进程 content URI 解码截图，在预览中点选后保存原图坐标与 RGB；该规则实际触发一次系统返回，并收到下一步骤 B 的 down/up/click。
- Android 16 验证导入会保留原有动作、容差、连续次数与间隔，不增加普通点击步骤；取消选择不改动已保存规则。
- Android 16 拒绝尺寸不符或损坏图片，并禁止以透明像素保存检测规则。

仪器测试的文件选择器结果由测试监视器提供；文件授权、跨进程读取、ImageDecoder、预览触摸、保存及实时检测使用真实系统接口。测试夹具恢复后的颜色与白色差异超过默认容差 16，避免将近似白色误当作颜色已离开。

原始日志和截图位于本机忽略目录 dist/checks/TongjiTap26、dist/checks/TongjiTap36。import-color-events.jsonl 记录一次 RECOVERY 和恢复后的 B 点击。正式包证据位于 dist/checks/Release26、dist/checks/Release36、dist/checks/Upgrade36。

## 已知边界

- 未测试真实手机、微信小程序、厂商后台策略、辅助功能共存、分屏或其他屏幕尺寸。不能据此保证第三方记录有效。
- 实时颜色检测需要 Android 11+，本轮颜色设备测试为 Android 16。基础点击包含 Android 8。
- 截图导入只设置位置和目标颜色，运行时仍采样当前屏幕。要求当前设备、当前方向的完整截图；尺寸不符会拒绝，不猜测缩放后的物理坐标。
- 颜色规则比较像素，不能识别业务页面。白色、浅灰色和闪屏可能落在同一容差范围，应选择差异明显的位置或调小容差。
- 受保护内容可能返回黑色遮罩。主体采样区域全黑时保守停止；不宣称能识别所有受保护窗口，正常全黑页面也会停止。
- 停止取消后续调度，不能保证撤回已提交手势。实时截图只在内存中采样；导入图片只在选点期间读取，不保存副本、不上传。
- 39 项设备场景使用调试构建；正式签名包另行检查安装、升级与系统选图，两者使用同一功能源码且不混淆。

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

设备脚本只允许本项目 TongjiTap26 / TongjiTap36 模拟器，会重设测试应用和系统开关。不要对日常设备运行。正式 APK 不包含 fixture、测试文件提供器或仪器测试代码。
