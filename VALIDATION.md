# 循济 TongjiTap 1.0.0 开发验证记录

日期：2026-11-23。使用本项目专用 AOSP 模拟器与独立测试页面。普通用户请直接使用 README 的下载入口。

## 构建与检查

| 项目 | 结果 |
| --- | --- |
| 单元测试 | 38 项通过，0 失败、0 跳过：执行状态机 15、颜色模型 4、截图坐标映射 4、绿点识别 2、跃点条件与里程解析 7、音乐配置与曲库 6 |
| Android Lint | 0 错误、0 警告 |
| Android 8 / API 26 | 原功能回归 15 项、正式界面 9 项、音乐集成 3 项通过 |
| Android 16 / API 36 | 原功能回归 24 项、正式界面 11 项、集成 6 项通过 |
| 正式 APK | tongjitap-1.0.0.apk；org.hachile.tongjitap；versionCode 4；versionName 1.0.0；最低 API 26、目标 API 36 |
| 签名与对齐 | RSA 3072 位；APK v2 签名有效，与 0.2.1 正式证书一致；zipalign 的 16 KB 检查通过；64 位 OCR 原生库的 PT_LOAD 对齐为 16384 |
| 正式包安装与升级 | Android 8 全新安装通过；Android 16 用正式签名包完成 0.2.1 → 1.0.0 覆盖升级，UID 不变、循环次数 13 保留；隐私确认、六页教程与四个页面均通过 |
| 权限声明 | 音乐网络访问、网络状态、媒体前台服务、唤醒锁和振动；不申请位置、通讯录、存储或 CHANGE_WIFI_STATE 权限；API 30+ 声明辅助功能截图能力 |
| 发行内容 | 通用 APK 包含 arm64-v8a、armeabi-v7a、x86、x86_64；不包含测试 APK、fixture、签名文件或用户截图 |

## 场景与证据

两个模拟器使用 1080 × 1920 屏幕、420 dpi、x86_64 AOSP 镜像。设备测试使用真实 Android API 与独立 fixture 页面；不会操作真实微信或校园网账号。

原功能回归包含主动同意、配置持久化与坏数据保护、编辑校验、排序禁用、取点不穿透、跨应用点击顺序和长按时序、倒计时与等待时停止、面板遮挡、旋转和锁屏停止、撤权与重连。API 36 另覆盖实时颜色触发返回与点击、持续命中停止、恢复期间手动停止、黑色遮罩停止，以及截图取色、参数保留和损坏图片拒绝。

正式界面测试覆盖：

- 首次隐私确认后完整走完六页教程；四栏导航与可展开的四个版本日志。
- 中文、英文、日语、德语和明暗主题；启动次数持久化、第五次启动的一次性 Star 邀请，界面重建不重复计数。
- 默认悬浮面板隐藏取点按钮；明确选择悬浮取点后才显示。
- QQ 群复制值恰为 934139029，底部项目链接正确，30sp / 20sp / 16sp 字号与原生图标比例保留。
- API 26 和 API 36 均能从截图添加点击步骤；API 36 框选地图、里程区域与 Wi-Fi 坐标后正确保存。预览信息栏固定高度，避免选点过程中因字体变化引起坐标偏移。
- API 36 使用内置模型识别测试里程，不依赖首次下载 OCR 模型。

音乐集成测试在两个系统上解码真实 WAV 音频，验证保持音高的 1.25 倍播放、实际拖动手势改变播放器倍速、后台进度继续增长及暂停。在线歌单和音频接口在本轮没有重新进行联网播放验证。

API 36 的跃点集成测试使用辅助功能截图、真实 ML Kit OCR 和实际系统快捷设置：先识别 0.100 km，再确认绿点移动与 0.150 km 后关闭 Wi-Fi；返回页面后确认反向移动与 0.200 km，再开启 Wi-Fi。两次切换都读取实际开关状态并收起系统面板。另验证独立的开关操作与屏幕尺寸变化清除旧校准。

测试中修正了快捷面板过渡动画、互联网弹窗收起、悬浮面板遮蔽前台窗口判断、慢速 OCR 帧间隔和返回跳变基准保留。调试版本允许独立 fixture 作为检测页面，正式版仅允许微信位于前台；正式 APK 不包含该测试例外。

原始日志与截图保存在本机忽略目录 `dist/checks/TongjiTap26/1.0.0/`、`dist/checks/TongjiTap36/1.0.0/` 的 `regression`、`formal`、`integration` 子目录。正式签名包证据在 `dist/checks/Release26/1.0.0/` 和 `dist/checks/Release36/1.0.0/`。

## 已知边界

- 未在真实手机、TJ-WIFI 或微信运动页面现场验证；模拟器测试不代表第三方接受运动记录，也不保证厂商快捷面板和后台策略兼容。
- 实时颜色和跃点需要 Android 11+，本轮设备验证使用 API 36；基础点击、截图步骤和音乐另覆盖 API 26。
- 用户须自行启用移动数据、保存校园网密码并开启自动连接。应用不读取这些凭据，也无法核实所有厂商系统的自动连接设置。
- 截图选点要求当前设备、当前方向的完整屏幕截图。分辨率、旋转或页面布局改变后须重新校准。OCR 区域应仅含当前里程，不能混入计时或目标距离。
- 绿点检测是图像特征判断，地图缩放、平移、其他绿色标记和 OCR 错读仍可能影响结果。双条件加下一帧确认减少误触，不能消除所有误判。
- 停止取消后续调度，不能保证撤回已提交手势；停止后 Wi-Fi 保持当前状态。实时截图仅在内存处理，不保存或上传；音乐服务会联网，ML Kit 会发送使用和性能指标，隐私说明已披露。
- 设备场景使用调试构建，正式签名包另查安装、升级与主要界面；两者使用同一功能源码，不混淆。没有物理手机续航、蓝牙耳机或大量曲库压力测试。

## 复现命令

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-core.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle.ps1 :app:assembleDebug :app:assembleDebugAndroidTest :fixture:assembleDebug
python scripts/setup-emulators.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-emulator.ps1 -Api 26
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-emulator.ps1 -Api 36
# 等待两个专用模拟器启动后，在每个系统依次执行三个套件：
python scripts/check-device.py --serial emulator-5556 --suite regression
python scripts/check-device.py --serial emulator-5556 --suite formal
python scripts/check-device.py --serial emulator-5556 --suite integration
python scripts/check-device.py --serial emulator-5558 --suite regression
python scripts/check-device.py --serial emulator-5558 --suite formal
python scripts/check-device.py --serial emulator-5558 --suite integration
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-release.ps1
# 覆盖测试还需要保留 dist/tongjitap-0.2.1.apk（可从旧 Release 下载）。
python scripts/smoke-release.py
```

设备脚本只允许本项目 TongjiTap26 / TongjiTap36 模拟器，会重新安装测试应用并改变测试环境的 Wi-Fi 状态。不要对日常设备运行。`scripts/smoke-release.py` 还会核验最终 APK 权限清单。
