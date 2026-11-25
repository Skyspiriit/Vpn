#!/usr/bin/env bash
# Amin VPN on-device check for the Xiaomi TV box (or any Android device) over adb.
#
# Touches ONLY com.enigma.aminvpn. It never installs, updates or uninstalls any other package
# (Amin Player, Amin Live TV, Amin Gateway are left alone).
#
# Usage:  scripts/device-test.sh [path/to/Amin-VPN.apk]      (device via USB or `adb connect IP:5555`)
# Output: a report under build/device-report-<time>.txt with no keys, tickets or relay addresses.
set -euo pipefail

PKG=com.enigma.aminvpn
APK="${1:-app/build/outputs/apk/release/app-release.apk}"
mkdir -p build
REPORT="build/device-report-$(date +%Y%m%d-%H%M%S).txt"
log() { echo "$*" | tee -a "$REPORT"; }
sh_() { adb shell "$@" 2>/dev/null | tr -d '\r'; }

adb get-state >/dev/null || { echo "No adb device. Connect USB or run: adb connect <box-ip>:5555" >&2; exit 1; }

log "== Device"
log "model:   $(sh_ getprop ro.product.manufacturer) $(sh_ getprop ro.product.model)"
log "android: $(sh_ getprop ro.build.version.release) (API $(sh_ getprop ro.build.version.sdk))"
log "abilist: $(sh_ getprop ro.product.cpu.abilist)"
log "abi32:   $(sh_ getprop ro.product.cpu.abilist32)"
log "memory:  $(sh_ grep -E 'MemTotal|MemAvailable' /proc/meminfo | tr -s ' ' | paste -sd ' ')"
log "time:    $(sh_ date)   (a wrong clock breaks TLS: fix it in Settings first)"

log "== APK"
log "file:    $APK ($(du -h "$APK" | cut -f1))"
unzip -l "$APK" | grep "lib/" | tee -a "$REPORT"

log "== Install (only $PKG)"
adb install -r "$APK" | tee -a "$REPORT"
log "installed size: $(sh_ du -sh /data/app/*${PKG}* 2>/dev/null | head -1 || echo n/a)"

adb logcat -c
log "== Launch"
sh_ am start -W -n "$PKG/com.enigma.amingateway.MainActivity" | tee -a "$REPORT"
cat <<'MSG'

On the TV: press the CENTER button of the remote (big ring = "اتصال").
The first time, Android shows a VPN permission dialog: choose OK / تأیید.
Waiting up to 120 s for the app to report a VERIFIED connection...
MSG

ok=""
for _ in $(seq 1 120); do
  line="$(adb logcat -d -s AminVpn:I | grep -E 'verified_connected|connect_failed' | tail -1 || true)"
  if [[ "$line" == *verified_connected* ]]; then ok="$line"; break; fi
  if [[ "$line" == *connect_failed* ]]; then break; fi
  sleep 1
done
log "== Connect"
if [ -n "$ok" ]; then log "VERIFIED: ${ok##*AminVpn: }  (DNS + pinned HTTPS passed through the tunnel)"; else log "NOT verified (see diagnostics below)"; fi

log "== Android VPN network"
sh_ dumpsys connectivity | grep -E "VPN|tun[0-9]" | grep -vE "[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+" | head -8 | tee -a "$REPORT" || true
log "tun interface: $(sh_ ip -o link show | grep -o 'tun[0-9]*' | head -1 || echo none)"

log "== Memory (app process)"
sh_ dumpsys meminfo "$PKG" | grep -E "TOTAL( PSS)?:|Native Heap|Java Heap" | head -4 | tee -a "$REPORT" || true

log "== Diagnostics (redacted by the app)"
adb logcat -d -s AminVpnDiag:I | tail -1 | sed 's/.*AminVpnDiag: //' | tee -a "$REPORT" || true
echo "(Open 'عیب‌یابی' on the TV once to refresh this line.)"

cat <<'MSG'

Manual checks on the TV while connected (record results in the report):
  1. Open a browser / YouTube: pages load?
  2. Farsiland: open https://farsiland.com in the TV browser (or the app you normally use).
  3. Press the ring again -> "قطع اتصال": internet should keep working without the VPN.
  4. Connect again 3 times in a row; each should turn turquoise.
  5. While connected, unplug/replug the network (or toggle Wi-Fi): it should recover by itself.
MSG
log "report: $REPORT"
