**Experimental test build: not yet tested on the Xiaomi box or in Iran.** Do not treat it as a stable release.

Amin VPN is a one-button VPN for 32-bit ARMv7 Android TV boxes, using OpenRung's open-source engine (sing-box + connectcore). No configuration or account is needed.

| | |
|---|---|
| File | `Amin-VPN-armv7-experimental.apk` (15.8 MB) |
| SHA-256 | `58a095fc325b86eb33f9fce5f174e2374f0bb44525728d60daff81066687df5e` |
| ABI | armeabi-v7a only (one native library, `libbox.so`) |
| Android | 8.0+ (API 26), Android TV launcher supported |
| Package | `com.enigma.aminvpn` (installs beside Amin Gateway; does not touch Amin Player / Amin Live TV) |
| Unit tests | 160/160 pass |

### Install (TV box)
1. Set the correct date and time on the box.
2. Copy the APK to a USB stick, open it with a file manager on the box and allow installation, or run `adb install Amin-VPN-armv7-experimental.apk`.
3. Open **Amin VPN**, press the centre button of the remote, and accept Android's VPN dialog («تأیید» / OK).
4. **Turquoise ring + «متصل است ✓»** means traffic passed through the tunnel. Red means it failed: open «عیب‌یابی» and send a photo.

### نصب (فارسی)
۱. تاریخ و ساعت دستگاه را درست کنید. ۲. فایل APK را نصب کنید. ۳. «Amin VPN» را باز کنید و دکمهٔ وسط کنترل را بزنید؛ در پنجرهٔ اندروید «تأیید» را بزنید. ۴. حلقهٔ فیروزه‌ای یعنی اتصال واقعاً آزمایش و برقرار شده است.

Source code (GPL-3.0, including the corresponding source for the engine): branch `amin-vpn-openrung-armv7`, see `docs/AMIN_VPN.md`.
