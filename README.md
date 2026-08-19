# 时感

时感是一个「时间感知播放器」：每一秒一声滴答，每一分一声卡塔，每一时一声叮。用校准过的墙钟对齐秒边界，锁屏后也继续走时。

- 应用名：时感
- 包名：`com.cea.timesense`
- 界面语言：简体中文
- 技术：Kotlin、Jetpack Compose、Foreground Service（`mediaPlayback`）

本仓库是完整可编译的源码工程。需要本机安装 **Android Studio 与 Android SDK** 才能构建、安装。这里没有随附已签名的 Play 商店 APK。

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
```

## 使用

1. 打开应用，大时钟显示当前时刻（若校时成功，已加上 NTP 偏移）。
2. 点 **走时**：若是 Android 13+，会先申请通知权限（用于前台通知）。然后每秒发声。
3. 息屏后仍继续走时。通知栏显示「时感正在走时」和当前时刻，可从通知点「停止」。
4. 点 **停止** 会结束服务、释放唤醒锁、撤掉通知。

声音规则：

| 时刻 | 声音 |
| --- | --- |
| 普通秒 | 滴答（`tick.wav`） |
| 每分 00 秒（非整点） | 卡塔（`kata.wav`），代替滴答 |
| 每时 00 分 00 秒 | 叮（`ding.wav`），优先于卡塔和滴答 |

音量走系统媒体音量（`STREAM_MUSIC`）。

## 声音资源

`app/src/main/res/raw/` 下的三个 WAV 由脚本生成（16-bit PCM、单声道、44.1 kHz），不依赖任何外部二进制素材：

```bash
python3 scripts/generate_sounds.py
```

- `tick.wav`：约 55 ms，偏高、短促的机械表「滴答」
- `kata.wav`：约 115 ms，更低、偏木质的「卡塔」
- `ding.wav`：约 780 ms，带泛音与衰减的铃「叮」

## 走时与校时（实现要点）

- **对齐秒**：`ClockScheduler` 用 NTP 校正后的墙钟算出「下一秒 000 ms」，再按 `elapsedRealtime` 睡眠。不使用固定的 `sleep(1000)`，因此不会累积漂移。
- **校时**：`TimeSync` 用 SNTP（UDP 123）询问 `time.google.com`，失败则 `pool.ntp.org`、`time.cloudflare.com`。打开应用时校一次；走时过程中每 12 小时再校。失败则保留上次偏移（或系统时钟），约 15 分钟后重试。界面显示上次校时时间与偏移。
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
    ui/theme/
  app/src/main/res/raw/{tick,kata,ding}.wav
  scripts/generate_sounds.py
```

## 限制

- 本环境未安装 Android SDK，因此仓库内**没有**预先编好的 debug APK。请在 Android Studio 中 Sync 后自行安装。
- 校时需要能访问公网 UDP 123。若网络拦截 NTP，应用会退回系统时钟并稍后重试。
- 个别省电策略可能在长时间后台后仍限制前台服务；若遇到中断，把时感加入电池优化白名单即可。
