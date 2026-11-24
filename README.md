<p align="center"><img src="./docs/images/tongjitap-icon.svg" width="100" alt="循济图标"></p>
<h1 align="center">循济 TongjiTap</h1>
<p align="center">一款支持自动点击、音乐调速和 Wi-Fi 切换的 Android 应用。</p>
<p align="center"><u>这个作者朋友面善又友善，因此允许刷锻一次走两圈</u></p>

<p align="center">🇨🇳 <a href="./README.md">简体中文</a> | 🇺🇸 <a href="./README.en.md">English</a></p>

<p align="center"><a href="https://github.com/GPDdev/TongjiTap/releases/latest/download/tongjitap.apk"><img src="./docs/images/download.svg" width="224" height="60" alt="下载安卓版"></a></p>
<p align="center"><a href="https://github.com/GPDdev/TongjiTap/releases/latest/download/tongjitap.apk"><img src="./docs/images/download-qr.png" width="225" height="225" alt="扫码下载最新版循济 Android APK"></a></p>
<p align="center">安卓手机扫码下载最新版</p>

点击和音乐功能支持 Android 8 及以上版本，颜色检测与跃点需要 Android 11 及以上版本。目前暂无 iOS 版本

## 使用说明

第一次打开会显示隐私政策和使用教程。以后想重看教程，在“更多”里就能找到。

<table><tr><th>循济</th><th>随步</th><th>跃点</th><th>更多</th></tr><tr><td><img src="./docs/images/loop-1.0.0.png" width="220" alt="循济点击设置"></td><td><img src="./docs/images/stride-1.0.0.png" width="220" alt="随步调速播放器"></td><td><img src="./docs/images/relay-1.0.0.png" width="220" alt="跃点截图校准"></td><td><img src="./docs/images/more-1.0.0.png" width="220" alt="更多与个人设置"></td></tr></table>

### 循济 Loop

1. 截取目标页面的完整屏幕截图，导入后点选要点击的位置。
2. 设置每一步按多久、等多久，以及执行顺序和循环次数。
3. 按引导开启辅助功能，打开悬浮面板，回到目标页面后点“开始”。随时可以停止或关闭面板。

默认从截图选点。选择“悬浮窗选点”后，面板才会显示取点按钮。请用当前手机未经裁剪的原始截图；旋转屏幕、修改分辨率或页面布局变化后，需要重新选点。

“颜色触发”可以在屏幕某处出现指定颜色时，执行返回或点击。先在截图里选好位置、颜色和动作，运行时检测的是当前屏幕。默认每 500 毫秒检测一次，连续两次检测到才执行，等颜色消失后继续。页面一直没恢复或无法获取屏幕时，会停止执行。

部分手机需要先在应用信息中允许“受限制的设置”，才能开启辅助功能。

### 随步 Stride

音乐功能来自 [StrideTune](https://github.com/GPDdev/StrideTune)。导入本地音频，或粘贴网易云公开歌单链接，就能播放。滑动调速区域可调整速度，也能设置速度范围、手势和保持音高，支持后台播放、耳机控制、循环和随机播放。

在线歌曲可能因版权、地区或平台限制无法播放，可以换成自己已有的本地音频。在线播放会消耗网络流量；应用不提供账号登录、会员绕过或 NCM 解密。

### 跃点 Relay

1. 打开 Wi-Fi 和移动数据，让 TJ-WIFI 保存密码并自动连接。应用不会读取校园网密码。
2. 导入运动中的完整截图，框选地图和当前里程。里程框里只保留当前里程，避开时间、目标距离等数字。
3. 再导入展开快捷设置后的截图，选中 Wi-Fi 开关，有些手机叫“互联网”。打开跃点面板，将它移出检测区域，开始后回到运动页面并保持页面可见。

绿点明显移动、同时里程增加超过 0.02 km，并经下一次检测确认后，跃点才会切换 Wi-Fi，先关再开，循环执行。绿点要移动多远才算触发，可以在应用里调整。

切换失败、持续识别失败、锁屏或屏幕尺寸变化时会停止。停止后 Wi-Fi 保持当时的状态，需要时请手动恢复。不同手机要分别校准，识别速度也会不同；目前尚未完成真实校园网和微信运动页面的现场验证。

### 更多 More

查看版本和更新日志、重看教程，或切换中文、English、日本語、Deutsch。

## 隐私与使用提醒

应用没有广告。设置和曲库等数据保存在手机的应用数据中，截图只在本机内存中处理，不保存副本、不上传。自动操作需要你授权并手动开始。

在线音乐会连接音乐平台。里程识别在本机完成，但使用的 ML Kit 会向 Google 发送性能和使用指标，详见 [SDK 隐私说明](https://developers.google.com/ml-kit/terms)。完整隐私政策可在“更多”查看。

循济是个人项目，与同济大学、体育部门、微信及音乐平台无官方关系。请遵守学校和平台规则，不把异常里程当作真实运动记录。自动点击、返回或切换网络可能误操作；应用无法保证记录或成绩有效，也不能保证账号不受限制。

## 联系与反馈

QQ 交流群：**934139029**

遇到问题，可以[提交 Issue](https://github.com/GPDdev/TongjiTap/issues)，附上手机型号、Android 版本和复现步骤。开发者主页：[home.hachile.org](https://home.hachile.org)。

<details>
<summary>从源码构建</summary>

Windows 环境需要 Git、Python 3.8+ 和 JDK 17 或 21。SDK 与 Gradle 会下载到项目的 `tools` 目录。

```powershell
git clone https://github.com/GPDdev/TongjiTap.git
cd TongjiTap
python scripts/bootstrap.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-release.ps1
```

安装包输出到 `dist/tongjitap-1.0.0.apk`。应用源码在 `app/`，设备测试页面在 `fixture/`，构建脚本在 `scripts/`。检查方法见 [VALIDATION.md](./VALIDATION.md)，测试 APK 不包含在正式安装包内。

请勿提交 `.signing`、本机配置、SDK 或缓存。自行构建的签名与官方安装包不同，后续更新需要沿用同一签名。

</details>

源码采用 [Apache License 2.0](./LICENSE)。版权与来源见 [NOTICE](./NOTICE)，第三方组件遵循各自条款，见[第三方声明](./THIRD_PARTY_NOTICES.md)。
