# Amin VPN: OpenRung ARMv7 integration notes

## Architecture decision

OpenRung's Android app is a React Native UI over a Kotlin `VpnService` host and a Go engine
(`connectcore`, `brokerapi`, `punchcore`, `wsscore`) compiled into sing-box's gomobile `libbox.aar`.
Only `bridge/`, `share/`, `MainActivity` and `MainApplication` depend on React Native. Amin VPN therefore:

* copies the Kotlin host packages `vpn`, `net`, `state`, `model`, `telemetry` and `config` verbatim
  (only the `R`, `BuildConfig` and `MainActivity` imports are changed), with their unit tests;
* drives the host exactly as OpenRung's RN module does: `VpnService.prepare` → consent →
  `OpenRungVpnService.connectIntent(DEFAULT_BROKER_URL)`, `disconnectIntent`, and observes `OpenRungStatusStore.uiState`;
* links **one** `libbox.aar`, so there is one Go runtime and no second VPN engine. Capsule (legacy mode) runs in Check
  Point's own app and contributes no native code.

Everything security-relevant stays in OpenRung code and is unchanged: signed relay-list verification
(brokerapi), broker TLS/ECH, the punch-coordinator certificate pins (`AppConfig`), WSS ticket handling,
installation key (Android Keystore), `cleartextTrafficPermitted=false`, routing-loop prevention (`protect()` on
engine sockets), tunnel DNS (fresh DoH nonce probe), and the rule that the status becomes CONNECTED only after
`verifyPath` passes DNS **and** pinned HTTPS through the VPN `Network`.

## Why 32-bit works

Upstream's `build-libbox-release.sh` already builds `jni/armeabi-v7a/libbox.so`. Upstream's
release APK drops it only through a Gradle packaging exclude. Amin VPN keeps only that slice
(`ndk.abiFilters = armeabi-v7a`, and the other ABIs are excluded). Measured from this build:

| Item | Value |
| --- | --- |
| `lib/armeabi-v7a/libbox.so` | 42.6 MB uncompressed, ELF32 ARM EABI5, NEEDED: liblog, libdl, libc only |
| Signed release APK (R8, compressed JNI) | ~15.8 MB |
| Other native libraries in the APK | none |

## Validation so far (build container, no device)

* Upstream Go tests (`punchbridge`, `TestOpenRungLibbox` with `-race`, sing-box TLS patch test): pass.
  Three telemetry-durability tests fail only when run as root, which defeats their `chmod` checks. The engine is
  built as an unprivileged user, as on CI, where they pass.
* Kotlin unit tests (OpenRung host + contract vectors under Robolectric): 158/158 pass.
* `aapt2`: `native-code: 'armeabi-v7a'`, leanback launcher + banner, touchscreen not required.

Not yet done: no Android device, emulator (no KVM) or Iranian network path was available here.
Nothing here claims real connectivity.

## Device test plan (home network, Xiaomi box)

1. Enable *Developer options → USB/Network debugging* on the box; `adb connect <box-ip>:5555`.
2. `scripts/device-test.sh Amin-VPN-armv7-experimental.apk`: records Android version, ABIs, RAM, clock,
   installs **only** `com.enigma.aminvpn`, launches, waits for `verified_connected ms=…` (connection time),
   VPN network/tun presence and app memory (PSS).
3. On the TV: browse, open Farsiland, disconnect, reconnect 3×, unplug/replug the network while connected.
4. Attach the generated `build/device-report-*.txt` (redacted: no IPs, URLs, tokens).

## Installing in Tehran (testers)

1. Copy `Amin-VPN-armv7-experimental.apk` to a USB stick. On the box, open it with a file manager
   (for example, *X-plore* or *File Commander*) and allow "install unknown apps" for that file manager.
   Or sideload with `adb install`.
2. Set the correct date and time (*Settings → Device preferences → Date & time*). A wrong clock is the most
   common reason for «ساعت و تاریخ دستگاه نادرست است».
3. Open **Amin VPN** from the apps row. Press the centre button and accept Android's VPN dialog («تأیید» / OK) once.
4. Wait for the turquoise ring and «متصل است ✓». Only then is the connection verified.
5. If it stays red, open «عیب‌یابی» and photograph the screen; it contains no secrets.
6. Optional: *Settings → Network → VPN → Amin VPN → Always-on* keeps it connected after reboot.

Success in Iran is **unconfirmed** until a tester in Tehran reports it.

## Remaining limitations

* No live test yet (box, Portugal, Tehran).
* OpenRung's broker/relay reachability from Iran depends on OpenRung's infrastructure, which this repository does not control.
* The engine reports OpenRung app version `1.0.1` (from `third_party/openrung/package.json`) to the broker for
  compatibility gating. Update it together with the upstream pins.
* Split tunneling (bypass Iranian sites) is supported by the engine and the rule sets are bundled, but there is
  no UI yet; it stays off (full tunnel).
* Legacy Capsule mode is reachable from «عیب‌یابی» and refuses to open while Amin VPN is active. Android allows
  one VPN at a time, so starting Capsule revokes Amin VPN cleanly (`onRevoke` → engine stop).

## Updating OpenRung

Copy the files listed in `third_party/openrung` from a new upstream commit, update `UPSTREAM_COMMIT`,
re-copy `android/app/src/main/java/com/openrung/{vpn,net,state,model,telemetry,config}` and its tests,
re-apply the three import edits, rebuild the AAR, and run the unit tests.
