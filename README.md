# Amin VPN (experimental, ARMv7)

A one-button VPN for the 32-bit ARMv7 Xiaomi Android TV box, built on **OpenRung**'s open-source
VPN engine. There is nothing to configure: no subscription link, server address, account or keyboard.
Press the big ring with the remote's centre button and the app picks a server automatically.

**[Download Amin VPN 0.1.1](https://github.com/Skyspiriit/Vpn/releases/download/v0.1.1-armv7-experimental/Amin-VPN.apk)** ·
[release notes and install steps](releases/RELEASE_NOTES.md) · [SHA-256](releases/Amin-VPN-armv7-experimental.apk.sha256)

> **Status: experimental test build.** The engine builds and the ported unit tests pass. Connectivity
> has **not** yet been verified on the Xiaomi box or in Iran. See [docs/AMIN_VPN.md](docs/AMIN_VPN.md).

| Colour | Meaning (Persian on screen) |
| --- | --- |
| Grey | Disconnected: «اتصال قطع است» |
| Yellow (sweeping) | Connecting: «در حال اتصال… لطفاً صبر کنید»; pressing again cancels |
| Turquoise | Connected **and verified**: fresh DNS + certificate-pinned HTTPS passed *through the tunnel* |
| Red | Failed, with a plain reason (for example, wrong clock or no internet); press again to retry |

D-pad: the ring has focus at start (bright outline). **Down** moves to «عیب‌یابی» (diagnostics), which shows
status, server location, connection time, network, ABI, memory and redacted engine log lines, plus
«اتصال دوباره» (reconnect) and the legacy University mode.

## How it works

* `app/src/main/java/com/openrung/**` supplies the Android VPN host, broker transport and tunnel checks.
  Amin disables app connection counting, process discovery, device metadata and engine telemetry.
  Its committed connectcore privacy patch prevents analytics sessions and IP geolocation requests.
* `app/src/main/kotlin/com/enigma/amingateway/` is the Galactic UI (`GatewayConsole`, `GalacticPanel`), the
  new `MainActivity` (Amin VPN), `Diagnostics`, and the preserved legacy Capsule code (`LegacyCapsuleActivity`,
  `GatewayConnection`, `CapsuleClient`, `UniversityProbe`).
* `third_party/openrung/` holds the pinned upstream build inputs (sing-box pin + patch, `punchbridge` Go
  bindings, `build-libbox-release.sh`, test vectors, rule sets, licence and notices) from upstream commit
  `UPSTREAM_COMMIT`. The script builds **one** `libbox.aar` (one Go runtime) with an `armeabi-v7a` slice.

Application ID: `com.enigma.aminvpn` (installs beside, never over, the old `com.enigma.amingateway`).

Version 0.1.1 uses a new release signing key configured for GitHub Actions. An installation of the
earlier experimental 0.1.0 APK requires removal before installing 0.1.1 because its signing key differs.
Future releases use the saved 0.1.1 signing key.

## Build

Requirements: JDK 17, Go 1.25.x, Android SDK 35 + NDK `29.0.14206865`, sagernet gomobile v0.1.12.

```bash
go install github.com/sagernet/gomobile/cmd/gomobile@v0.1.12 && go install github.com/sagernet/gomobile/cmd/gobind@v0.1.12
ANDROID_NDK_HOME=$ANDROID_HOME/ndk/29.0.14206865 bash third_party/openrung/android/build-libbox-release.sh   # run as a non-root user
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

A signed release needs `AMIN_KEYSTORE_FILE`, `AMIN_KEYSTORE_PASSWORD`, `AMIN_KEY_ALIAS` and `AMIN_KEY_PASSWORD`;
without them `assembleRelease` refuses to build (it never falls back to a debug key). CI:
`.github/workflows/armv7-experimental.yml` (secrets `AMIN_KEYSTORE_BASE64`, `AMIN_KEYSTORE_PASSWORD`,
`AMIN_KEY_ALIAS`, `AMIN_KEY_PASSWORD`). Tags `v*-experimental*` publish a GitHub **pre-release** only.

## Licence

The APK statically links sing-box/libbox (GPL-3.0-or-later), so the distributed app is GPL-3.0-or-later
([LICENSE](LICENSE)). The corresponding source for any APK is this repository at the matching commit,
including `third_party/openrung` and the Go module pins it records. Third-party notices:
[third_party/openrung/THIRD_PARTY_NOTICES.md](third_party/openrung/THIRD_PARTY_NOTICES.md).

## Legacy mode: Amin Gateway (University of Aveiro / Check Point Capsule)

A one-button Android companion for the University of Aveiro VPN. Check Point Capsule VPN signs you in and carries the traffic. Amin Gateway creates the UA site through Check Point's documented site API, sends connect and disconnect requests, and verifies the connection before lighting the turquoise ring.

[Download the APK](Amin-Gateway.apk?raw=true) · [Preview the connected screen (simulation)](Amin-Gateway-connected-preview.png)

### Install on your Samsung tablet

The project folder includes the installable `Amin-Gateway.apk` alongside this guide.

1. Install **Check Point Capsule VPN** by Check Point Software Technologies from [Google Play](https://play.google.com/store/apps/details?id=com.checkpoint.VPN).
2. Copy `Amin-Gateway.apk` to the tablet and open it in **My Files**. If Samsung asks, allow installation from that source, then tap **Install**. The APK is a debug-signed personal build for Android 8.0 or later.
3. Open **Amin Gateway** and tap **Connect to the Galaxy**. Capsule should open the setup for **Amin-Gateway-UA** at `go.ua.pt`. Complete the site setup there, return to Amin Gateway, then tap **Connect to the Galaxy** to start the VPN.
4. In Capsule, sign in with your UA Universal User account (`yourname@ua.pt`) and password. Complete any university prompts and accept Android's VPN consent if it appears. If Capsule shows an unexpected certificate or fingerprint prompt, verify it with [UA IT support](https://bud.ua.pt/) before approving it. Keep the site name **Amin-Gateway-UA** for the app's connect and disconnect controls.
5. Return to Amin Gateway if Capsule stays in the foreground. Once the turquoise glow appears, open your usual browser for research. Tap **Disconnect from the Galaxy** when finished. Capsule controls whether credentials can be remembered and when you need to sign in again.

If Capsule connects during site creation, disconnect that first session in Capsule and start the next one from Amin Gateway so it can verify the session. UA lists IPsec first. If it fails, UA's guide suggests changing the tunnel type to SSL in Capsule's site settings. Use Capsule's own app to read and resolve any password, MFA, certificate, and gateway errors.

If Capsule cannot accept the site or its control links, open Capsule directly. Create a site for `go.ua.pt` with username and password authentication, then use Capsule to connect and disconnect. Amin Gateway will label a tunnel it cannot attribute to the requested site as unverified. The manual Capsule workflow is UA's documented Android path. [UA remote-access guide](https://www.ua.pt/pt/stic/teletrabalho_vpn.)

### Check library access

From a network outside UA, connect through Capsule and open a fresh private tab in your normal browser. Open a known subscription-only article from b-on or another UA-licensed database and check that the full text or PDF opens with institutional access. For comparison, disconnect and repeat in a new private session without a publisher or institutional login. A public landing page, abstract, or open-access article cannot confirm subscription access. [UA library guidance](https://api-portal.ua.pt/api/v1/file/76788)

### Open the source project

Clone this repository or download it as a ZIP from the **Code** menu. Open the project folder in Android Studio. Use JDK 17 and install Android SDK Platform 35. The project pins Gradle 8.13, Android Gradle Plugin 8.13.2 and Kotlin 2.2.21. On Windows, run `gradlew.bat :app:assembleDebug :app:lintDebug` from the project directory. On macOS or Linux, run `sh gradlew :app:assembleDebug :app:lintDebug`. The build writes `app/build/outputs/apk/debug/app-debug.apk`. [Android build-tool compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)

The original artwork is drawn by `GalacticPanel.kt`, with a native Android vector launcher icon in `ic_gateway.xml`. The screen uses native controls, responds to window size and system insets, and follows Android's animation setting.

### Integration and security

- **UA's published configuration.** Android uses Capsule VPN with IPsec or SSL at `go.ua.pt`. The documented sign-in uses an eligible UA Universal User account and password. UA retired PPTP and documents the separate SSL Network Extender workflow for Linux. I found no UA-published Android profile for generic IKEv2, OpenVPN, or WireGuard, and no public Android Check Point protocol SDK documented for direct use against this gateway. Android `VpnService` requires a compatible VPN protocol engine. Android `VpnManager` provisions platform-supported profiles and has no Capsule protocol engine. [UA remote-access guide](https://www.ua.pt/pt/stic/teletrabalho_vpn.) [Android VPN framework](https://developer.android.com/develop/connectivity/vpn) [Android VpnManager](https://developer.android.com/reference/android/net/VpnManager)
- **Official-client handoff.** The app targets Capsule's Play Store package with `cpvpn` site creation, connect, and disconnect links. Site creation requests `remoteActions=yes`. Capsule may bring its own screen to the foreground. It owns the tunnel, Android permission prompt, background notification, and authentication policy. The site API documents success redirects and does not expose structured login errors to other apps. [Check Point site API, pp. 17–20](https://community.checkpoint.com/fyrhh23835/attachments/fyrhh23835/sandblast-mobile/1235/1/CP_CapsuleVPNClient_AdminGuide.pdf#page=17) [Capsule VPN on Google Play](https://play.google.com/store/apps/details?id=com.checkpoint.VPN)
- **Connection verification.** ON requires a one-time response for the current UA connection attempt, a newly observed VPN network from Android, and a successful hostname-verified TLS handshake to `my.ua.pt` through a route on that specific network. It monitors network loss and rechecks reachability while the console is visible. The probe checks UA connectivity. Use the browser test above to verify a licensed resource. Android restricts another VPN app's owner identity, so an existing VPN or a tunnel that outlives Amin Gateway's process is shown as unverified. Capsule can manage it directly. [Android NetworkCapabilities](https://developer.android.com/reference/android/net/NetworkCapabilities#getOwnerUid()) [Android Network](https://developer.android.com/reference/android/net/Network) [UA portal](https://my.ua.pt/)
- **Account data and errors.** Amin Gateway handles no university credentials. Its sole saved setting records whether it sent the site setup request. If a handoff fails or times out, the app offers retry or opens Capsule. Capsule displays account, gateway, and certificate messages. For local diagnosis, `adb logcat -s AminGateway:I` shows reason codes for launch, timeout, and route checks with no credentials or response tokens.

### Verification and remaining device tests

- **Verified in an Android 14 emulator.** APK install, fresh launch, portrait and landscape layout, main screen reopening, rejection of an unsolicited response, timeout recovery, and native rendering of the turquoise glow in a labeled visual preview.
- **Verified with a local test fixture.** The app sends the configured UA site and connect requests, accepts its matching one-time response, and stays unverified when the response arrives without a VPN. The fixture was used only in the emulator. The installable app has no preview mode.
- **Still to verify on the Samsung tablet.** The current Capsule client must accept the site setup and control links and return to Amin Gateway after connection. Test live UA sign-in, any MFA or certificate prompts, reconnect with Capsule's credential policy, loss of the live tunnel, disconnect from Amin Gateway, and full-text journal access in the tablet browser. The supported fallback is to connect and disconnect directly in Capsule.
