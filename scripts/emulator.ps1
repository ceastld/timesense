# CLI helper so 时感 can be installed and driven without Android Studio.
param(
    [Parameter(Position = 0)]
    [ValidateSet("start", "wait", "install", "launch", "run", "stop", "status", "dump-ui")]
    [string]$Command = "run"
)

$ErrorActionPreference = "Stop"

$AvdName = "timesense_api29"
$PackageId = "com.cea.timesense"
$LaunchActivity = "com.cea.timesense/.MainActivity"
$RepoRoot = Split-Path -Parent $PSScriptRoot
$ApkPath = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$Adb = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"
$Emulator = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"

function Get-AdbDevices {
    & $Adb devices | Select-Object -Skip 1 | Where-Object { $_.Trim() -ne "" }
}

function Get-EmulatorSerial {
    $line = Get-AdbDevices | Where-Object { $_ -match "^emulator-\d+\s+device" } | Select-Object -First 1
    if ($line -match "^(emulator-\d+)") {
        return $Matches[1]
    }
    return $null
}

function Test-EmulatorProcess {
    return [bool](Get-Process -Name "qemu-system-x86_64", "emulator" -ErrorAction SilentlyContinue)
}

function Start-TimesenseEmulator {
    if (Get-EmulatorSerial) {
        Write-Host "emulator already online: $(Get-EmulatorSerial)"
        return
    }
    if (Test-EmulatorProcess) {
        Write-Host "emulator process is starting, waiting..."
        return
    }
    Write-Host "starting AVD $AvdName"
    Start-Process -FilePath $Emulator -ArgumentList @(
        "-avd", $AvdName,
        "-gpu", "host",
        "-netdelay", "none",
        "-netspeed", "full"
    ) | Out-Null
}

function Wait-TimesenseEmulator {
    $deadline = (Get-Date).AddMinutes(8)
    Write-Host "waiting for emulator boot..."
    while ((Get-Date) -lt $deadline) {
        $serial = Get-EmulatorSerial
        if ($serial) {
            $boot = (& $Adb -s $serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
            if ($boot -eq "1") {
                Write-Host "emulator ready: $serial"
                return $serial
            }
        }
        Start-Sleep -Seconds 3
    }
    throw "emulator did not boot within 8 minutes"
}

function Install-TimesenseApk {
    if (-not (Test-Path $ApkPath)) {
        throw "debug APK missing: $ApkPath  (run .\gradlew.bat :app:assembleDebug first)"
    }
    $serial = Wait-TimesenseEmulator
    Write-Host "installing $ApkPath"
    & $Adb -s $serial install -r -t $ApkPath
    if ($LASTEXITCODE -ne 0) {
        throw "adb install failed"
    }
}

function Start-TimesenseApp {
    $serial = Wait-TimesenseEmulator
    Write-Host "launching $LaunchActivity"
    & $Adb -s $serial shell am start -n $LaunchActivity | Out-Null
}

function Stop-TimesenseEmulator {
    $serial = Get-EmulatorSerial
    if ($serial) {
        & $Adb -s $serial emu kill
        return
    }
    Get-Process -Name "qemu-system-x86_64", "emulator" -ErrorAction SilentlyContinue |
        Stop-Process -Force
}

function Show-TimesenseStatus {
    Write-Host "AVD: $AvdName"
    Write-Host "APK: $ApkPath  exists=$(Test-Path $ApkPath)"
    Write-Host "adb devices:"
    Get-AdbDevices | ForEach-Object { Write-Host "  $_" }
}

function Dump-TimesenseUi {
    $serial = Wait-TimesenseEmulator
    $out = Join-Path $RepoRoot "app\build\ui-dump.xml"
    New-Item -ItemType Directory -Force -Path (Split-Path $out) | Out-Null
    & $Adb -s $serial shell uiautomator dump /sdcard/ui-dump.xml | Out-Null
    & $Adb -s $serial pull /sdcard/ui-dump.xml $out | Out-Null
    Write-Host "wrote $out"
}

switch ($Command) {
    "start" { Start-TimesenseEmulator }
    "wait" { Wait-TimesenseEmulator | Out-Null }
    "install" { Install-TimesenseApk }
    "launch" { Start-TimesenseApp }
    "run" {
        Start-TimesenseEmulator
        Install-TimesenseApk
        Start-TimesenseApp
    }
    "stop" { Stop-TimesenseEmulator }
    "status" { Show-TimesenseStatus }
    "dump-ui" { Dump-TimesenseUi }
}
