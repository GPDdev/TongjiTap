# 已完成：循济 TongjiTap 1.0.0 本地开发

## 1. 当前目标
正式版 1.0.0 本地开发和必要验证已完成。四栏为循济 Loop、随步 Stride、跃点 Relay、更多 More；包含启动界面、音乐整合、双条件网络切换、截图默认选点、四语言与主题、首次教程、第五次启动 Star 提醒。项目地址 https://github.com/GPDdev/TongjiTap。远程发行状态以 Releases 和本机 handoffs/2026-11-23-tongjitap-1.0.0.md 为准。iOS 暂缓，原图标继续使用。

## 2. 完成条件
38 项单元测试、Android 8 的 27 项和 Android 16 的 41 项设备场景通过；Lint 0 错误、0 警告。正式包签名及 16 KB 对齐检查通过，Android 8 冷安装和 Android 16 从 0.2.1 覆盖升级通过，循环次数保留。中英文 README、日志、版权声明、版本及仓库属性配置同步。详细证据与局限见 VALIDATION.md。

## 3. 已确认事实
应用 ID org.hachile.tongjitap，versionName 1.0.0、versionCode 4，复用已有正式签名。后续未另行指定时递增为 1.0.1，并增加 versionCode。Android 8 支持基础点击、截图步骤和音乐；实时颜色与跃点需要 Android 11。跃点要求绿点大幅移动且里程严格增加超过 20 米，再确认一帧；操作系统快捷面板后验证实际 Wi-Fi 状态。测试夹具已完成关、开两次联动，未验证真实校园网或微信运行页面。

## 4. 相关文件与命令
MainActivity、Ui、AppPreferences、L 和 assets/i18n 管理界面、四语言及主题；ScreenshotImport 和 ScreenshotPickerView 管理本机截图选点及区域；StridePage、PlaybackService 等整合 StrideTune；RelayMonitor、RelayGate、GreenDot、WifiPanel 管理绿点、OCR、条件确认与网络切换。

scripts/test-core.ps1、scripts/check-device.py、scripts/build-release.ps1 和 scripts/smoke-release.py 为复现入口。正式包 dist/tongjitap-1.0.0.apk；证据在 dist/checks。发布资产使用稳定文件名 tongjitap.apk，使 README 的 releases/latest/download/tongjitap.apk 指向最新版。源码归档使用 Git archive，禁止打包工作目录。开发分支 codex/tongjitap-1.0.0，目标 main，标签 v1.0.0。

## 5. 已否决方案
不调用普通应用不可用的 setWifiEnabled；不读取校园网密码。不按裁剪图猜测坐标，不以单独位置变化或单独里程增长切换网络。保留原 ID、签名和 Apache-2.0。StrideTune 的未发布许可证草稿未被当作授权，整合来自项目所有者的明确指令，来源在 NOTICE 中记录。

## 6. 风险与待确认项
无本地开发待办。真实手机、厂商系统、TJ-WIFI 和微信现场表现仍需用户设备验证；本轮未重新联网验证音乐平台接口。截图仅本机内存处理；在线音乐会联网，ML Kit 的使用与性能指标已披露。停止后 Wi-Fi 保持当时状态。不得提交 .signing、用户截图、SDK、缓存和本机配置。

## 7. 下一步动作
无，本版本地开发已完成；远程发行核验记录在本机版本交接文件。后续需求另开版本。
