# Amin Gateway

A one-button Android companion for the University of Aveiro VPN. Check Point Capsule VPN signs you in and carries the traffic. Amin Gateway creates the UA site through Check Point's documented site API, sends connect and disconnect requests, and verifies the connection before lighting the turquoise ring.

[Download the APK](Amin-Gateway.apk?raw=true) · [Preview the connected screen (simulation)](Amin-Gateway-connected-preview.png)

## Install on your Samsung tablet

The project folder includes the installable `Amin-Gateway.apk` alongside this guide.

1. Install **Check Point Capsule VPN** by Check Point Software Technologies from [Google Play](https://play.google.com/store/apps/details?id=com.checkpoint.VPN).
2. Copy `Amin-Gateway.apk` to the tablet and open it in **My Files**. If Samsung asks, allow installation from that source, then tap **Install**. The APK is a debug-signed personal build for Android 8.0 or later.
3. Open **Amin Gateway** and tap **Connect to the Galaxy**. Capsule should open the setup for **Amin-Gateway-UA** at `go.ua.pt`. Complete the site setup there, return to Amin Gateway, then tap **Connect to the Galaxy** to start the VPN.
4. In Capsule, sign in with your UA Universal User account (`yourname@ua.pt`) and password. Complete any university prompts and accept Android's VPN consent if it appears. If Capsule shows an unexpected certificate or fingerprint prompt, verify it with [UA IT support](https://bud.ua.pt/) before approving it. Keep the site name **Amin-Gateway-UA** for the app's connect and disconnect controls.
5. Return to Amin Gateway if Capsule stays in the foreground. Once the turquoise glow appears, open your usual browser for research. Tap **Disconnect from the Galaxy** when finished. Capsule controls whether credentials can be remembered and when you need to sign in again.

If Capsule connects during site creation, disconnect that first session in Capsule and start the next one from Amin Gateway so it can verify the session. UA lists IPsec first. If it fails, UA's guide suggests changing the tunnel type to SSL in Capsule's site settings. Use Capsule's own app to read and resolve any password, MFA, certificate, and gateway errors.

If Capsule cannot accept the site or its control links, open Capsule directly. Create a site for `go.ua.pt` with username and password authentication, then use Capsule to connect and disconnect. Amin Gateway will label a tunnel it cannot attribute to the requested site as unverified. The manual Capsule workflow is UA's documented Android path. [UA remote-access guide](https://www.ua.pt/pt/stic/teletrabalho_vpn.)

## Check library access

From a network outside UA, connect through Capsule and open a fresh private tab in your normal browser. Open a known subscription-only article from b-on or another UA-licensed database and check that the full text or PDF opens with institutional access. For comparison, disconnect and repeat in a new private session without a publisher or institutional login. A public landing page, abstract, or open-access article cannot confirm subscription access. [UA library guidance](https://api-portal.ua.pt/api/v1/file/76788)

## Open the source project

Clone this repository or download it as a ZIP from the **Code** menu. Open the project folder in Android Studio. Use JDK 17 and install Android SDK Platform 35. The project pins Gradle 8.13, Android Gradle Plugin 8.13.2 and Kotlin 2.2.21. On Windows, run `gradlew.bat :app:assembleDebug :app:lintDebug` from the project directory. On macOS or Linux, run `sh gradlew :app:assembleDebug :app:lintDebug`. The build writes `app/build/outputs/apk/debug/app-debug.apk`. [Android build-tool compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes)

The original artwork is drawn by `GalacticPanel.kt`, with a native Android vector launcher icon in `ic_gateway.xml`. The screen uses native controls, responds to window size and system insets, and follows Android's animation setting.

## Integration and security

- **UA's published configuration.** Android uses Capsule VPN with IPsec or SSL at `go.ua.pt`. The documented sign-in uses an eligible UA Universal User account and password. UA retired PPTP and documents the separate SSL Network Extender workflow for Linux. I found no UA-published Android profile for generic IKEv2, OpenVPN, or WireGuard, and no public Android Check Point protocol SDK documented for direct use against this gateway. Android `VpnService` requires a compatible VPN protocol engine. Android `VpnManager` provisions platform-supported profiles and has no Capsule protocol engine. [UA remote-access guide](https://www.ua.pt/pt/stic/teletrabalho_vpn.) [Android VPN framework](https://developer.android.com/develop/connectivity/vpn) [Android VpnManager](https://developer.android.com/reference/android/net/VpnManager)
- **Official-client handoff.** The app targets Capsule's Play Store package with `cpvpn` site creation, connect, and disconnect links. Site creation requests `remoteActions=yes`. Capsule may bring its own screen to the foreground. It owns the tunnel, Android permission prompt, background notification, and authentication policy. The site API documents success redirects and does not expose structured login errors to other apps. [Check Point site API, pp. 17–20](https://community.checkpoint.com/fyrhh23835/attachments/fyrhh23835/sandblast-mobile/1235/1/CP_CapsuleVPNClient_AdminGuide.pdf#page=17) [Capsule VPN on Google Play](https://play.google.com/store/apps/details?id=com.checkpoint.VPN)
- **Connection verification.** ON requires a one-time response for the current UA connection attempt, a newly observed VPN network from Android, and a successful hostname-verified TLS handshake to `my.ua.pt` through a route on that specific network. It monitors network loss and rechecks reachability while the console is visible. The probe checks UA connectivity. Use the browser test above to verify a licensed resource. Android restricts another VPN app's owner identity, so an existing VPN or a tunnel that outlives Amin Gateway's process is shown as unverified. Capsule can manage it directly. [Android NetworkCapabilities](https://developer.android.com/reference/android/net/NetworkCapabilities#getOwnerUid()) [Android Network](https://developer.android.com/reference/android/net/Network) [UA portal](https://my.ua.pt/)
- **Account data and errors.** Amin Gateway handles no university credentials. Its sole saved setting records whether it sent the site setup request. If a handoff fails or times out, the app offers retry or opens Capsule. Capsule displays account, gateway, and certificate messages. For local diagnosis, `adb logcat -s AminGateway:I` shows reason codes for launch, timeout, and route checks with no credentials or response tokens.

## Verification and remaining device tests

- **Verified in an Android 14 emulator.** APK install, fresh launch, portrait and landscape layout, main screen reopening, rejection of an unsolicited response, timeout recovery, and native rendering of the turquoise glow in a labeled visual preview.
- **Verified with a local test fixture.** The app sends the configured UA site and connect requests, accepts its matching one-time response, and stays unverified when the response arrives without a VPN. The fixture was used only in the emulator. The installable app has no preview mode.
- **Still to verify on the Samsung tablet.** The current Capsule client must accept the site setup and control links and return to Amin Gateway after connection. Test live UA sign-in, any MFA or certificate prompts, reconnect with Capsule's credential policy, loss of the live tunnel, disconnect from Amin Gateway, and full-text journal access in the tablet browser. The supported fallback is to connect and disconnect directly in Capsule.
