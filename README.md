# 轻点助手

个人开发的离线 Android 坐标点击器。支持多步骤、整组循环、屏幕取点、手动坐标和可拖动控制面板，兼容目标为 Android 8.0 及以上。

本应用与同济大学、学校体育部门、微信及其他应用运营者无隶属或合作关系。自动点击可能误触，也可能影响第三方记录或账号；应用不保证运动里程、成绩有效性或规避平台处理。应用内提供完整用途、隐私与风险说明，首次使用需要主动确认。

## 安装与使用

1. 安装 `dist/lighttap-0.1.0.apk`。如系统询问安装来源，请自行核实文件来源后决定是否允许。
2. 打开应用，阅读完整说明，勾选后进入。
3. 点击“辅助功能权限设置”，在系统辅助功能中找到“轻点助手”，自行开启服务。该权限可以控制其他应用的触摸操作。部分 Android 13 及以上系统对侧载应用限制辅助功能；仅在确信来源可信时，按系统应用信息页提供的“允许受限制的设置”等选项操作。厂商入口可能不同，应用不会绕过系统限制。
4. 设置整组循环次数。可以手动添加步骤，也可以打开悬浮面板、切换到目标页面，再点“取点”选择位置。选点只记录坐标，不点击下方应用。
5. 回到应用编辑每步的按住时长、释放后等待时间和顺序。取点新增步骤默认按住 50 毫秒、等待 500 毫秒。
6. 在目标页面移动面板，避开所有点击坐标。拖动面板标题可以移动，点击标题可左右移位。点击“开始”，倒计时 3 秒后执行。
7. 点击“停止”取消后续调度，“关闭”也会停止并移除面板。已提交的手势可能仍会完成或由系统取消。可在系统辅助功能设置中彻底关闭服务。

坐标以当前屏幕像素为单位，从屏幕左上角开始。屏幕尺寸或方向变化后，需要清空旧步骤并重新取点；页面按钮移动和弹窗无法自动识别，请自行检查。显示缩放、导航模式、分屏等变化也应重新校准。

## 执行规则

- 整组依次执行所有步骤，算作一次循环；只支持有限次数，不自动开机运行或恢复任务。
- 循环次数 1～100000，按住时长 1～10000 毫秒，释放后等待 0～3600000 毫秒。
- 每步收到系统手势完成回调后，再开始等待。最后一步的等待用于下一轮；最后一轮完成后直接结束。
- 毫秒值是调度请求，实际时序受设备和系统影响，不保证实时精度。长按可能触发目标应用的长按动作。
- 运行期间配置不可修改。屏幕关闭、锁定、尺寸或方向变化、手势失败、回调超时、辅助功能中断或撤销时，停止后续调度。停止后重新开始，会从第 1 步开始。
- 面板遮挡点击点时拒绝启动；运行中发现遮挡也会停止。面板无法代替你判断当前页面是否正确。

## 隐私与权限

不申请联网、定位、存储、录屏或普通悬浮窗权限，不读取聊天、页面节点或账号内容，不接入广告、统计和云同步。辅助功能只声明手势能力；面板使用辅助功能悬浮窗口。仅在私有空间保存一组配置、屏幕信息和说明确认状态，云备份及系统数据迁移均显式排除。卸载或清除应用数据会删除配置。

## 从源码构建

需要 Windows、Python 3、JDK 17 或 21，以及访问 Google、Gradle、Maven 下载站点的网络。无需全局安装 Android Studio。脚本将工具和缓存放在本项目 `tools` 中，不修改系统 PATH。

```powershell
python scripts/bootstrap.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-core.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-release.ps1
```

首次构建会创建 `.signing/lighttap.jks` 和配套配置文件。**私下备份整个 `.signing` 目录**，后续覆盖升级必须使用同一个签名，且递增 `versionCode`。不要把这个目录、密码或密钥放入 Git、分享包或交接文件。Android Studio 可直接打开本目录；本机 SDK 路径在忽略的 `local.properties` 中。

固定构建组合：AGP 8.13.2、Gradle 8.13、compileSdk / targetSdk 36、minSdk 26、Java 17 源码。应用没有第三方运行时依赖；单元测试使用 JUnit 4.13.2。Gradle Wrapper 固定分发文件 SHA-256，SDK 归档按官方索引中的校验值核验。

## 检查与测试

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle.ps1 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :fixture:assembleDebug
python scripts/setup-emulators.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-emulator.ps1 -Api 26
# 等待模拟器启动，再运行：
python scripts/check-device.py --serial emulator-5556
# Android 16 使用 -Api 36 和 emulator-5558。
# 两个项目模拟器同时启动后，可冷安装检查发布 APK：
python scripts/smoke-release.py
```

`fixture` 是独立测试 APK，仅用于记录真实触摸的按下、释放、点击和长按事件，不包含在交付 APK 中。`DeviceChecks` 是设备仪器测试，检查确认流程、配置、取点、跨应用手势、停止、旋转、熄屏和权限撤销。

**设备测试仅限可丢弃模拟器**：测试会重设测试应用数据，改变模拟器辅助功能开关、屏幕方向与亮屏状态。不要对日常手机直接运行。`scripts/check-device.py` 会拒绝真实设备，并要求指定项目创建的 LightTap 模拟器。

最终实际检查结果见 `VALIDATION.md`；不要将模拟器验证视为真实手机或微信小程序的兼容性证明。

## 项目结构

- `app`：主界面、私有配置、辅助功能服务、纯 Java 执行状态机，以及单元和设备测试。
- `fixture`：独立触摸测试页面，只用于开发验证。
- `scripts`：本地工具下载、测试、签名和构建脚本。
- `HANDOFF.md`：当前交付状态和后续接手入口。

源码许可尚未指定。本次为本地交付，不含商店上架或远程发布。
