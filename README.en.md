# Tongji TapLoop / 轻循

<div align="center"><img alt="Version 0.1.0" src="https://img.shields.io/badge/version-0.1.0-2563eb"><img alt="Android 8.0 and above" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84"><img alt="Java 17" src="https://img.shields.io/badge/Java-17-ed8b00"><img alt="Recorded checks: 10 unit and 22 emulator scenarios" src="https://img.shields.io/badge/recorded_checks-10_unit_%2B_22_emulator-16a34a"><img alt="License Apache-2.0" src="https://img.shields.io/badge/license-Apache--2.0-blue"></div>

<p align="center">🇨🇳 <a href="./README.md">简体中文</a> | 🇺🇸 <a href="./README.en.md">English</a></p>

An offline Android coordinate-tapping assistant with ordered steps, configurable timing, and finite loops.

[Download APK](https://github.com/GPDdev/Tongji-TapLoop/releases/latest) | [Usage](#usage) | [Build from source](#build-from-source) | [Validation record](./VALIDATION.md)

> [!IMPORTANT]
> This is an independently developed project. It is not affiliated with, authorized by, or partnered with Tongji University, its sports department, or WeChat. “Tongji” in the repository name does not imply official endorsement. The current 0.1.0 APK and screenshots retain the initial display name “轻点助手”; the application ID is `org.hachile.lighttap`.

## Contents

- [Overview](#overview)
- [Installation](#installation)
- [Usage](#usage)
- [Parameters and execution](#parameters-and-execution)
- [Permissions and privacy](#permissions-and-privacy)
- [Build from source](#build-from-source)
- [Validation and compatibility](#validation-and-compatibility)
- [Project structure and interfaces](#project-structure-and-interfaces)
- [Contributing](#contributing)
- [Risks and disclaimer](#risks-and-disclaimer)
- [License](#license)

## Overview

TapLoop executes user-configured tap steps in order and repeats the sequence a specified number of times. Each step has its own coordinates, hold duration, and wait after release. A visible panel provides start, stop, and coordinate-picking controls for repetitive touch assistance or interface testing on devices and interfaces you are authorized to operate.

- **Two ways to set coordinates:** enter screen pixels manually or switch to the target interface and pick a position through the overlay. Picking does not tap the app underneath.
- **Ordered, finite loops:** add, edit, delete, and reorder steps; repeat the entire sequence a finite number of times.
- **Visible controls:** a three-second countdown, current loop and step indicators, and a draggable panel. Tapping its title moves it between sides.
- **Stops on errors or screen changes:** screen-off, locking, screen size or orientation changes, gesture failures, callback timeouts, and accessibility interruptions stop future scheduling.
- **Local configuration:** saves one set of steps and settings, without advertising, analytics, cloud sync, or automatic execution after a restart.

<table><tr><th>Main screen</th><th>Floating controls</th></tr><tr><td><img src="./docs/images/home-android16.png" alt="Main screen on Android 16, displaying the initial name 轻点助手" width="260"></td><td><img src="./docs/images/panel-android8.png" alt="Floating controls over the independent fixture app on Android 8" width="260"></td></tr></table>

## Installation

1. Download `lighttap-0.1.0.apk` and its `.sha256` file from [GitHub Releases](https://github.com/GPDdev/Tongji-TapLoop/releases/latest). Android 8.0 or later is required.
2. Verify the file's source before installation. You can calculate its SHA-256 with the command below and compare it with the downloaded checksum:

   ```powershell
   Get-FileHash .\lighttap-0.1.0.apk -Algorithm SHA256
   ```

3. On first launch, read the purpose, privacy, and risk notice, then explicitly check the consent box to continue.
4. Tap “辅助功能权限设置” (accessibility settings), find “轻点助手” in the system's accessibility settings, and enable the service yourself. This authorization allows cross-app touch gestures; you can disable it in the same settings at any time.

> [!NOTE]
> Some Android 13 and later systems restrict accessibility access for sideloaded apps. Only if you trust the installation source, follow the system's own app-info options, such as “Allow restricted settings.” Vendor menus vary; this app does not bypass system restrictions. The current app interface is in Chinese.

## Usage

1. Set the number of whole-sequence loops, then add steps manually or open the floating panel.
2. Switch to the target interface, tap “取点” (pick a point), and select a position. Each selection adds a step with a 50 ms hold and a 500 ms wait after release.
3. Return to the app to adjust each step's timing and execution order.
4. Move the floating panel away from all tap coordinates on the target interface, then tap “开始” (start). Execution begins after a three-second countdown.
5. Tap “停止” (stop) at any time. “关闭” (close) also stops execution and removes the panel. A new run starts from step one.

**Example:** on a test page without important data, select positions A and B. Set A to hold for 50 ms and wait for 300 ms; set B to hold for 700 ms and wait for 350 ms. Repeat twice. The order is A → B → A → B; B may trigger the target interface's long-press action.

## Parameters and execution

| Parameter | Range or meaning | Default |
| --- | --- | --- |
| Whole-sequence loops | 1–100000; completing all steps counts as one loop | 10 |
| X / Y coordinates | Screen pixels measured from the top-left corner, within the screen bounds | Picked or entered by the user |
| Hold duration | 1–10000 ms | 50 ms |
| Wait after release | 0–3600000 ms | 500 ms |

The wait starts after the system reports that a gesture has completed. The final step's wait is used before the next loop; the last loop finishes immediately after its final gesture. Configuration cannot be changed during execution. The app refuses to start if the panel covers a tap coordinate, and stops if it detects this overlap during execution.

Millisecond values are scheduling requests, not real-time guarantees. Timing depends on device and system load. Coordinates are associated with the saved screen size and orientation; clear old steps and pick them again after a change. The app does not recognize moving buttons or pop-ups. Recalibrate after changes to display scaling, navigation mode, or split-screen use as well.

## Permissions and privacy

- Uses a manually authorized `AccessibilityService` to dispatch gestures and an accessibility overlay for the control panel.
- Does not request network, location, storage, screen-recording, or ordinary overlay permissions; does not read page nodes, chats, or account contents.
- Stores one configuration, screen information, and notice-confirmation state in private app storage. Cloud backup and system data transfer are explicitly excluded.
- No login, advertising, analytics, or telemetry. Uninstalling the app or clearing its data removes the configuration.

## Build from source

The supplied setup, signing, and validation scripts target **Windows**. They require Git, Python 3.8 or later, JDK 17 or 21, and network access to Google, Gradle, and Maven download servers. A global Android Studio installation is not required. The scripts place the SDK, Gradle, and caches in the project's `tools` directory.

```powershell
git clone https://github.com/GPDdev/Tongji-TapLoop.git
cd Tongji-TapLoop
python scripts/bootstrap.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/test-core.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/build-release.ps1
```

Outputs are `dist/lighttap-0.1.0.apk` and its `.sha256` file. The pinned build stack is AGP 8.13.2, Gradle 8.13, compileSdk / targetSdk 36, minSdk 26, and Java 17 source. The production app has no third-party runtime dependencies; unit tests use JUnit 4.13.2.

> [!IMPORTANT]
> The build script creates a private local `.signing` directory. Back it up securely. In-place upgrades require the same signing key and an increased `versionCode`. Never commit or share keys, passwords, or machine-specific configuration. A newly generated signing key differs from the Releases APK's key, so that build cannot directly replace the APK published by this repository.

<details>
<summary>Development checks and emulator test commands</summary>

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gradle.ps1 :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :fixture:assembleDebug
python scripts/setup-emulators.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/start-emulator.ps1 -Api 26
# 等待启动，再运行 Android 8.0 的设备检查：
python scripts/check-device.py --serial emulator-5556
# Android 16 使用 -Api 36 和 emulator-5558。
# 两个项目模拟器同时启动后，可检查签名发布包的冷安装：
python scripts/smoke-release.py
```

Device checks are for disposable LightTap26 / LightTap36 emulators only. They reset test app data and change accessibility settings, orientation, and screen state. Do not run them on an everyday phone. `fixture` is a separate touch-recording test app and is not included in the distributed APK.

</details>

## Validation and compatibility

The recorded 0.1.0 validation includes **10 core unit tests** and **11 emulator scenarios each** on Android 8.0 / API 26 and Android 16 / API 36, for 22 device scenarios in total. The signed APK passed cold installation, explicit consent, and launch checks on both emulators. Release Lint, v2 signature verification, and zipalign checks passed. See [VALIDATION.md](./VALIDATION.md). The badge represents this recorded validation; it does not claim an existing continuous-integration setup.

Physical phones, vendor battery-management policies, coexistence with other accessibility services, split-screen use, display scaling, and WeChat mini programs remain unverified. Emulator results do not establish physical-device compatibility.

## Project structure and interfaces

| Directory | Purpose |
| --- | --- |
| `app/` | Native interface, private configuration, accessibility service, execution state machine, and tests |
| `fixture/` | Independent touch-test app for development validation only |
| `scripts/` | Tool downloads, builds, signing, and device checks |
| `docs/` | Screenshots and repository publication settings |

This is a standalone Android app. It currently exposes no HTTP API, external SDK, or APK command-line interface. Configure tap steps through the app interface.

## Contributing

Report problems through [Issues](https://github.com/GPDdev/Tongji-TapLoop/issues). Include the app version, phone model, Android version, screen orientation, reproduction steps, and expected behavior. Do not submit account information, chat screenshots, or other sensitive data.

Before opening a pull request, run the core unit tests and Android Lint. Changes to tapping, coordinate picking, or stopping should include reproduction and verification on an independent test page. Do not commit `.signing`, `local.properties`, SDKs, caches, or build outputs.

## Risks and disclaimer

> [!WARNING]
> Coordinate errors or interface changes can trigger sending, purchasing, or deletion. Calibrate on a test page without important data and avoid payment, account, or other sensitive interfaces. Keep the screen visible while running and stop immediately if anything looks wrong. Stopping cancels future scheduling; a gesture already submitted to the system may still finish or be cancelled by the system.

Automatic tapping and using third-party software faults to generate records may violate school requirements or platform rules. Check the applicable rules before use, and operate only devices and interfaces you are authorized to control. The project does not validate third-party records or guarantee increased distance, valid grades, approval, or freedom from account restrictions. It does not provide hidden execution, detection evasion, or platform-specific exploit parameters.

The project is provided under Apache-2.0. This section explains behavior and risks; it does not imply school or platform permission or promise exemption from liability. Refer to [LICENSE](./LICENSE) for the license's warranty and liability terms.

## License

Original project code is licensed under [Apache License 2.0](./LICENSE). See [NOTICE](./NOTICE) for the copyright notice. Third-party components, including the Gradle Wrapper, retain their respective copyright and license notices.
