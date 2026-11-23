<p align="center"><img src="./docs/images/tongjitap-icon.svg" width="100" alt="循济图标"></p>
<h1 align="center">循济 TongjiTap</h1>
<p align="center">设好步骤，把重复点击交给循济。</p>

<div align="center"><img alt="Version 0.2.1" src="https://img.shields.io/badge/version-0.2.1-2563eb"><img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84"><img alt="Java 17" src="https://img.shields.io/badge/Java-17-ed8b00"><img alt="Apache-2.0" src="https://img.shields.io/badge/license-Apache--2.0-blue"></div>

<p align="center">🇨🇳 <a href="./README.md">简体中文</a> | 🇺🇸 <a href="./README.en.md">English</a></p>

<p align="center"><a href="https://github.com/GPDdev/TongjiTap/releases/latest"><img src="./docs/images/download.svg" width="260" alt="点击进入 Releases 下载最新版"></a></p>

**[安卓下载 APK](https://github.com/GPDdev/TongjiTap/releases/latest/download/tongjitap-0.2.1.apk)**　|　[怎么使用](#怎么使用)　|　[颜色触发](#颜色触发)　|　[开发与反馈](#开发与反馈)

## 下载与安装

| 你的设备 | 下载什么 |
| --- | --- |
| Android 手机 | 下载 **`tongjitap-0.2.1.apk`**，安装后打开“循济” |
| iPhone / iPad | **暂无 iOS 版**，本轮暂缓开发 |
| 想看源码 | 下载 `TongjiTap-0.2.1-source.zip`，普通使用不需要 |

基础点击支持 Android 8 及以上；颜色触发需要 Android 11 及以上。首次使用时阅读说明，再按应用引导手动开启辅助功能。

> [!NOTE]
> 从 0.2.0 升级可直接覆盖安装，保留已有设置。应用 ID 为 `org.hachile.tongjitap`；从 0.1.0 升级会作为新应用安装，需要重新设置。部分手机还需在应用信息中允许“受限制的设置”。

## 怎么使用

1. **设次数**：填写整组步骤要循环多少次。
2. **选位置**：打开悬浮面板，切换到目标页面，点“取点”后选择位置。也可以手动输入坐标。
3. **调节奏**：回到循济，设置每一步按住多久、松开后等多久，并调整顺序。
4. **开始运行**：回到目标页面，移开遮挡点击位置的面板，点“开始”。倒计时 3 秒后执行。
5. **随时停止**：点“停止”或“关闭”。熄屏、旋转屏幕或权限断开也会停止。

蓝色按钮可以操作，灰色按钮当前不可用。所有步骤执行一遍算一次循环，最后一轮完成后自动结束。

<table><tr><th>设置步骤</th><th>悬浮面板</th></tr><tr><td><img src="./docs/images/home-android16.png" width="260" alt="循济步骤设置"></td><td><img src="./docs/images/panel-android8.png" width="260" alt="循济悬浮控制面板"></td></tr></table>

## 颜色触发

页面发生变化时，可以让循济先处理，再继续原来的步骤。例如：某个稳定位置变为指定颜色后，执行一次返回。

1. 截取目标页面，在循济的“颜色触发”中点 **“从截图选点取色”**，选择本机截图。
2. 在截图上点按或拖动，查看放大预览、坐标和颜色值，点 **“使用并开启”**。新规则默认执行手机返回；已有规则保留原来的动作和检测参数。
3. 如需点击指定位置，打开“设置颜色规则”更改动作；动作坐标也可通过悬浮面板的“动作点”选取。
4. 回到目标页面照常开始。默认每 500 毫秒检测一次，连续命中 2 次才触发，颜色容差为 16。

请选择当前手机、当前方向的**完整截图**，不要使用裁剪图或长截图。导入只用于设置检测点和目标颜色；运行时仍检测手机当前画面。也可以通过面板的“检测点”选择坐标，再手动填写颜色（白色为 `#FFFFFF`）。

<p><img src="./docs/images/screenshot-picker-android16.png" width="260" alt="从截图选择检测位置和颜色"></p>

命中后会等当前手势结束，再执行一次动作。随后等待 1 秒并确认目标颜色消失，再继续步骤；约 12 秒仍未恢复会停止，避免反复返回。颜色检测只在运行时开启。

选点时避开状态栏、悬浮面板和经常变化的区域，选择正常页面与异常页面颜色差异明显的位置。容差越大，越容易把相近颜色判为命中；白色与浅灰色难以区分时可调小容差。白色页面和闪屏可能误触发，请先测试。

受保护页面可能无法读取。截图出错或主体区域全黑时会停止；全黑页面不适合启用颜色规则。

## 隐私与使用提醒

无需登录，不联网，没有广告或统计。设置只保存在手机上。导入的截图仅在选点期间于本机读取，不保存副本、不上传。开启颜色规则后，应用会在内存中读取屏幕截图的指定像素，随即释放图片，**不保存、不上传**；不读取页面节点或识别文字。

循济由个人开发，与同济大学、体育部门及微信无隶属、授权或合作关系。请遵守学校和平台规则；本应用不保证运动记录或成绩有效。坐标点击与返回操作都可能误触，运行时保持屏幕可见，避开支付、账号及重要数据页面。完整说明可在应用内查看。

## 开发与反馈

**QQ交流群号：934139029**。应用内点击群号即可复制。

发现问题请到 [Issues](https://github.com/GPDdev/TongjiTap/issues) 留下手机型号、Android 版本和复现步骤。

<details>
<summary>从源码构建 Android 版</summary>

Windows 环境需要 Git、Python 3.8+ 和 JDK 17 或 21。SDK 和 Gradle 会下载到项目的 `tools` 目录。

```powershell
git clone https://github.com/GPDdev/TongjiTap.git
cd TongjiTap
python scripts/bootstrap.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-release.ps1
```

安装包输出到 `dist/tongjitap-0.2.1.apk`。应用源码在 `app/`，设备测试页面在 `fixture/`，构建脚本在 `scripts/`。检查命令和记录见 [VALIDATION.md](./VALIDATION.md)。没有对外 API。

提交前运行核心测试与 Android Lint。不要提交 `.signing`、本机配置、SDK 或缓存；请私下保管签名文件，后续更新继续复用。自行构建的签名与这里提供的安装包不同。

</details>

## 许可证

采用 [Apache License 2.0](./LICENSE)，版权声明见 [NOTICE](./NOTICE)。第三方组件保留各自的许可证。
