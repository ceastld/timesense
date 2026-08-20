# 时感

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![GitHub release](https://img.shields.io/github/v/release/ceastld/timesense)](https://github.com/ceastld/timesense/releases/latest)

时感（TimeSense）是一个开源的 Android「时间感知播放器」：每一秒一声滴答，每一分一声卡塔，每一时一声叮。用校准过的墙钟对齐秒边界，锁屏后也继续走时。

- 应用名：时感
- 包名：`com.cea.timesense`
- 界面语言：简体中文
- 技术：Kotlin、Jetpack Compose、Foreground Service（`mediaPlayback`）
- 许可：[MIT](LICENSE)

## 下载

到 [Releases](https://github.com/ceastld/timesense/releases/latest) 下载 APK，侧载安装。Android 8.0（API 26）及以上。

发版方式：给 `main` 打 `v主.次.修订` 标签并推送，GitHub Actions 会自动构建并创建 Release。

```bash
git tag v1.0.6
git push origin v1.0.6
```

`versionName` 取自标签（去掉 `v`）。`versionCode` 为 `major * 10000 + minor * 100 + patch`，例如 `v1.0.6` → `10006`。

可选：在仓库 Settings → Secrets and variables → Actions 中配置正式签名，否则 Release APK 使用 debug 签名（可覆盖本仓库先前的侧载包）。

| Secret | 说明 |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `.jks` / `.keystore` 的 base64 |
| `SIGNING_STORE_PASSWORD` | 密钥库密码 |
| `SIGNING_KEY_ALIAS` | 密钥别名 |
| `SIGNING_KEY_PASSWORD` | 密钥密码（可与库密码相同） |

```bash
base64 -w0 release.jks   # Linux / GitHub runner
```

## 使用

1. 打开应用，大时钟显示当前时刻（若校时成功，已加上 NTP 偏移）。
2. 点 **走时**：若是 Android 13+，会先申请通知权限（用于前台通知）。然后每秒发声。
3. 息屏后仍继续走时。通知栏显示「时感正在走时」和当前时刻，可从通知点「停止」。
4. 点 **停止** 会结束服务、释放唤醒锁、撤掉通知。
5. 主界面点按音效格试听，长按进入对应项设置。系统返回键在设置页会回到主界面。

声音规则：

| 时刻 | 声音 |
| --- | --- |
| 普通秒 | 滴答 |
| 每分 00 秒（非整点） | 滴答（50% 音量）+ 卡塔 |
| 每时 00 分 00 秒 | 滴答（50% 音量）+ 叮 |

音量走系统媒体音量（`STREAM_MUSIC`）。可在设置里选择内置音效或追加自定义文件。

## 在 Android Studio 中打开

1. 安装 [Android Studio](https://developer.android.com/studio)（建议 Ladybug / 2024.2 或更新）。
2. 通过 SDK Manager 安装：
   - Android SDK Platform 35
   - Android SDK Build-Tools 35.x
   - 已包含的 Android SDK Platform-Tools
3. **File → Open**，选中本目录（含 `settings.gradle.kts` 的那一层）。
4. 等待 Gradle Sync。第一次会下载 Gradle 8.10.2、AGP 8.7.2 和依赖，需要网络。
5. 连上一部 Android 8.0+（API 26）真机，或启动 API 26+ 模拟器。
6. 点 Run（绿色三角），运行 `com.cea.timesense`。

若 Android Studio 提示缺少 JDK，选 **Embedded JDK 17** 即可（工程 `jvmTarget` 为 17）。

命令行构建（已配置 SDK 时）：

```bash
# 若没有 local.properties，先写一行 sdk.dir=你的SDK路径
./gradlew :app:assembleDebug
# APK 输出：app/build/outputs/apk/debug/app-debug.apk

./gradlew :app:assembleRelease
# 未配置 TIMESENSE_KEYSTORE 时，release 使用 debug 签名
```

Windows 可用 `.\gradlew.bat`。模拟器辅助脚本：`scripts/emulator.ps1`。

## 声音资源

`app/src/main/res/raw/` 下的 WAV 由脚本生成（16-bit PCM、单声道、44.1 kHz），不依赖任何外部二进制素材：

```bash
python scripts/generate_sounds.py
```

内置每类四个变体（经典 / 轻柔或低沉等）。自定义音频会复制到应用私有目录后追加到对应列表。

## 走时与校时（实现要点）

- **对齐秒**：`ClockScheduler` 用 NTP 校正后的墙钟算出「下一秒 000 ms」，再按 `elapsedRealtime` 睡眠；最后几毫秒自旋，避免一次长 sleep 把节拍拉毛。
- **校时**：`TimeSync` 用 SNTP（UDP 123）询问 `time.google.com`，失败则 `pool.ntp.org`、`time.cloudflare.com`。打开应用时校一次；走时过程中每 12 小时再校。失败则保留上次偏移（或系统时钟），约 15 分钟后重试。
- **锁屏**：`TickService` 以前台服务（`foregroundServiceType=mediaPlayback`）运行，并持有 `PARTIAL_WAKE_LOCK`。停止时释放锁并移除通知。

## 权限

`INTERNET`、`WAKE_LOCK`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`POST_NOTIFICATIONS`、`RECEIVE_BOOT_COMPLETED`。

`RECEIVE_BOOT_COMPLETED` 已声明，但**不会**开机自启。

## 工程结构

```
TimeSense/
  settings.gradle.kts
  build.gradle.kts
  gradle/wrapper/
  .github/workflows/ci.yml       # main / PR 编 debug
  .github/workflows/release.yml  # 推送 v*.*.* tag 发 Release
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/cea/timesense/
    MainActivity.kt
    TimeSenseApp.kt
    TimeSenseStore.kt
    time/TimeSync.kt          # SNTP
    time/ClockScheduler.kt    # 对齐下一秒
    audio/SoundEngine.kt      # SoundPool
    service/TickService.kt    # 前台服务
    ui/TimeSenseScreen.kt
    ui/SettingsPanel.kt
    ui/theme/
  app/src/main/res/raw/*.wav
  scripts/generate_sounds.py
  scripts/emulator.ps1
```

## 限制

- 校时需要能访问公网 UDP 123。若网络拦截 NTP，应用会退回系统时钟并稍后重试。
- 个别省电策略可能在长时间后台后仍限制前台服务；若遇到中断，把时感加入电池优化白名单即可。
- 未配置 Release 签名时，GitHub Release 里的 APK 是 debug 签名，不能上 Play；侧载升级路径与本仓库先前包一致。
