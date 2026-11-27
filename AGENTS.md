# 版本约定

- 用户未另行指定时，每次版本迭代只将 `x.y.z` 的 `z` 加一，例如 `0.2.1` 更新为 `0.2.2`。
- 用户要求发布或更新到具体版本号时，默认发布正式版。使用 `vX.Y.Z` 标签并在 GitHub 标记为 Latest。只有用户明确要求预览版或原生蓝独立版时才发布独立渠道。
- 每个正式版本在同一次发版中同步以下四处，验证后直接提交、推送并发布，无需再问是否上传。

  1. 将源码推送到 GitHub，给正式 Release 附上 `tongjitap.apk`。
  2. 让 GitHub README 的「下载最新版」按钮直接下载该 APK。
  3. 让正式 App 的手动检查与自动更新提醒识别这个版本，并提供同一 APK。
  4. 在产品官网 `https://tap.hachile.org/` 同步版本说明和下载入口。

- 发布完成前，从 README 和线上官网的下载入口取回 APK，比对其 SHA-256 与正式 Release 的附件，并核对包名、签名和版本号。用旧正式版验证能发现新版，用当前正式版验证不会重复提示。
- 同步应用版本、构建产物文件名、README、变更记录和 GitHub Release；Android `versionCode` 也必须递增。
- 各平台只显示本平台和本渠道可安装的更新。正式 App 的检查和下载始终跟随正式 Release。
- 原生蓝独立版的包名为 `org.hachile.tongjitap.preview`。发布这个渠道时使用 `ui-vx.y.z` 标签和 `tongjitap-preview.apk` 附件，保留常规版的下载入口。
- 正式名称为“循济 / TongjiTap”，应用 ID 为 `org.hachile.tongjitap`。后续版本复用本机已有正式签名，确保可以覆盖更新。不得提交签名文件或密钥。
- 中文界面统一使用 `Ui` 中的字号：主标题 30sp、小标题 20sp、正文与按钮 16sp。
