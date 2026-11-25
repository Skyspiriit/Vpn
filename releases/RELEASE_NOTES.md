**Amin VPN 0.1.1 — device-tested experimental ARMv7 release.** Eight live checks passed on a Xiaomi MiTV-AFKR0 running Android 11. Connectivity from Iran still requires an on-location test.

Amin VPN is a one-button VPN for 32-bit ARMv7 Android TV boxes, using OpenRung's open-source engine (sing-box + connectcore). No configuration or account is needed.

| | |
|---|---|
| File | `Amin-VPN.apk` (15.8 MB) |
| SHA-256 | `3d61c12c904bdcecea5913fc58bb51f24d5f3509420070f8ac93b3990fac396b` |
| ABI | armeabi-v7a only (one native library, `libbox.so`) |
| Android | 8.0+ (API 26), Android TV launcher supported |
| Package | `com.enigma.aminvpn` (installs beside Amin Gateway; does not touch Amin Player / Amin Live TV) |
| Unit tests | 165/165 pass, zero skipped; native engine checks pass |

Version 0.1.1 disables engine analytics, analytics geolocation, app connection counting and device metadata reporting. It fixes cancellation error handling and stale connection states after process loss. The APK is signed by the release key configured in GitHub Actions.

Live checks covered DNS and HTTPS through the VPN, background operation, two disconnect/reconnect cycles, process loss, and recovery followed by a graceful disconnect. Verified connections took 8.9–13.3 seconds. [Full device report](../docs/DEVICE_TEST_REPORT.md).

The release signing key differs from the earlier 0.1.0 experimental key. Remove an old 0.1.0 installation before installing 0.1.1. Future updates use the saved 0.1.1 key.

### Install (TV box)
1. Set the correct date and time on the box.
2. Copy the APK to a USB stick, open it with a file manager on the box and allow installation, or run `adb install Amin-VPN.apk`.
3. Open **Amin VPN**, press the centre button of the remote, and accept Android's VPN dialog («تأیید» / OK).
4. **Turquoise ring + «متصل است ✓»** means traffic passed through the tunnel. Red means it failed: open «عیب‌یابی» and send a photo.

### نصب (فارسی)
۱. تاریخ و ساعت دستگاه را درست کنید. ۲. فایل APK را نصب کنید. ۳. «Amin VPN» را باز کنید و دکمهٔ وسط کنترل را بزنید؛ در پنجرهٔ اندروید «تأیید» را بزنید. ۴. حلقهٔ فیروزه‌ای یعنی اتصال واقعاً آزمایش و برقرار شده است.

Source code (GPL-3.0, including the corresponding source for the engine): tag `v0.1.1-armv7-experimental` and branch `main`, see `docs/AMIN_VPN.md`.
