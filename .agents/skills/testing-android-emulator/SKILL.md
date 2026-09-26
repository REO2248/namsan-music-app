---
name: testing-android-emulator
description: How to set up and end-to-end test an Android app on the emulator in this environment (KVM fix, AVD creation, UI interaction via adb/uiautomator, Korean/non-ASCII input via ADBKeyboard, system-bar touch traps).
---

# Android Emulator Testing on this Machine

## Environment / SDK
- SDK root: `/home/ubuntu/android-sdk` (has `platform-tools`, `platforms/android-35`, `build-tools`, `cmdline-tools/latest`). Add `platform-tools` to PATH.
- `local.properties` already contains `sdk.dir`. JDK 17 installed.
- The SDK may be missing `emulator` and `system-images` — install with:
  `ANDROID_SDK_ROOT=/home/ubuntu/android-sdk yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"`
- Create AVD: `~/android-sdk/cmdline-tools/latest/bin/avdmanager create avd -n <name> -k "system-images;android-35;google_apis;x86_64" -d pixel_6`

## KVM (critical)
- Machine supports KVM but the `ubuntu` user may not be in the `kvm` group → `emulator -accel-check` fails and software emulation is unusably slow.
- Fix: `sudo -n chmod 666 /dev/kvm` (passwordless sudo works). Re-run `-accel-check` → "KVM is installed and usable". Boot is then ~20s.

## Running for recordings
- Windowed (so screen recording shows the app): `DISPLAY=:0 nohup ~/android-sdk/emulator/emulator -avd <name> -gpu swiftshader_indirect -no-snapshot -wipe-data -no-boot-anim &`
- Desktop is 1600x1200 but computer-tool screenshots are 1024x768 (scale ÷1.5625). Position the window with `wmctrl -r "Android Emulator" -e 0,560,20,500,1130` (the separate 54px toolbar window may lag behind; cosmetic).
- Wait for boot: `adb wait-for-device` then poll `adb shell getprop sys.boot_completed` = 1.
- Dismiss the "Nested Virtualization" warning dialog if it pops up.

## Driving the app (no scrcpy installed)
- `adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml` exposes Compose semantics: `text`, `content-desc`, `bounds="[x1,y1][x2,y2]"`. Tap node centers via `adb shell input tap <cx> <cy>`.
- Compose bounds SHIFT when content changes (title length, scroll) — always re-dump before tapping; do not reuse stale coordinates.
- Screenshots: `adb exec-out screencap -p > out.png` (full 1080x2400).

## Non-ASCII / Korean text input
- `adb shell input text` CANNOT inject non-ASCII (crashes NPE on null key array).
- Solution: install ADBKeyboard once — `adb install ADBKeyboard.apk` (from github.com/senzhk/ADBKeyBoard raw), then `adb shell ime enable com.android.adbkeyboard/.AdbIME && adb shell ime set com.android.adbkeyboard/.AdbIME`, and inject via `adb shell am broadcast -a ADB_INPUT_TEXT --es msg '감나무'` into a focused EditText.
- WARNING: the IME window stays open and covers the bottom of the screen (`dumpsys window` → InputMethod frame ~[0,2223][1080,2400]) — it eats taps on the bottom nav bar. Press `input keyevent KEYCODE_BACK` once to dismiss before tapping nav items.

## System-bar touch trap (edge-to-edge apps)
- On API 35 google_apis emulator the StatusBar window frame is ~128px tall (`dumpsys window windows` → `Window...StatusBar ... frame=[0,0][1080,128]`) — taller than the reported inset. App top-bar controls drawn under it are touch-dead in the covered region even though visible. If a top-bar button "doesn't work", check `dumpsys window windows` frames and try the bottom sliver of the control (y just under 128).
- Enable `adb shell settings put system pointer_location 1` to see a crosshair proving where touches land (remember to `... 0` after).

## Assertions without UI
- Playback state: `adb shell dumpsys media_session` → `state=PlaybackState {state=PLAYING(3)|PAUSED(2)|ERROR(7), position=N, ...}`; sample twice to prove position advances; `active item id=N` tracks queue index; `queueTitle=null, size=N` = queue length.
- ExoPlayer errors: `adb logcat -d | grep -iE "ExoPlayerImplInternal|LoadTask"` shows the real exception (e.g. resolver IllegalStateException).
- Downloads: `dumpsys notification | grep providers.downloads` (completion channel) + `ls /sdcard/Music/<dir>`.
- Background: `input keyevent KEYCODE_HOME`, then `dumpsys notification` for the media notif + `pidof <pkg>` + media_session state.
- Rotation: `settings put system accelerometer_rotation 0; settings put system user_rotation 1` (1=landscape; restore 0). Verify `dumpsys window displays | grep cur=`.
- Crash sweep: `adb logcat -d -b crash` and `adb logcat -d | grep -E "FATAL|AndroidRuntime"`.
- First-run runtime perms (e.g. POST_NOTIFICATIONS on 33+): grant deterministically via `pm grant <pkg> android.permission.POST_NOTIFICATIONS` (after `pm clear` for a clean run) OR tap Allow — dialog button bounds are in the uiautomator dump.

## Devin Secrets Needed
- none (backend and tooling are unauthenticated; GitHub raw fetch for ADBKeyboard.apk only).

## This app's specifics
- Backend `http://www.gpsh.edu.kp` is plain HTTP, slow — generous timeouts; `GET rmenjoy/play?id=` pages resolve the real mp3 path; `rmenjoy/download?id=` streams ~3.5MB slowly (HEAD→502 quirk, GET works).
