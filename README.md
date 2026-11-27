# 循济 1.1.0 原生蓝独立版

Android 8 及以上版本请下载 **`tongjitap-preview.apk`**。装过原生蓝 `1.0.2-preview` 的用户可以直接覆盖安装，保留曲库和设置。也可以和常规版并排安装。

- 底栏依次为循济、跃点、漫迹、随步、更多。播放器通知仍指向随步。
- 在「随步」调节音乐倍速，在「跃点」查看手机的网络状态。漫迹为 iOS 预留，目前没有 iOS 安装包。
- 在「更多」点「检查更新」可查找同渠道的新版 APK。打开「自动更新提醒」后，应用启动时会向 GitHub 检查更新。下载和安装仍由你确认。

上一版原生蓝预览没有更新入口，首次升级到 1.1.0 需要手动安装。

[下载 Android 安装包](https://github.com/GPDdev/TongjiTap/releases/download/ui-v1.1.0/tongjitap-preview.apk) ｜ [GitHub Release](https://github.com/GPDdev/TongjiTap/releases/tag/ui-v1.1.0)

## 从源码构建

使用 JDK 17 或 21 和 Android SDK 36，在仓库根目录运行下列命令。Windows 使用 `gradlew.bat`。

```sh
./gradlew :preview:assembleDebug
```

APK 位于 `preview/build/outputs/apk/debug/preview-debug.apk`，应用 ID 为 `org.hachile.tongjitap.preview`。自己构建的 APK 使用本机签名。

QQ 交流群为 **934139029**。源码采用 Apache-2.0 许可证。[许可证](./LICENSE) ｜ [版权声明](./NOTICE) ｜ [第三方组件](./THIRD_PARTY_NOTICES.md)
