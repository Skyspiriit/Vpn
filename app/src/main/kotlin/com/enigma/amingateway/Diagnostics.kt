package com.enigma.amingateway

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import com.openrung.state.ConnectionStatus
import com.openrung.state.OpenRungStatusStore

/**
 * Simple, privacy-safe connection diagnostics. The report never contains broker tickets, WSS URLs,
 * keys or the installation identity: engine log lines are passed through [redact] (IP addresses,
 * URLs and long token-like strings are masked) and only the last few lines are shown.
 */
internal object Diagnostics {
    private const val LOG_LINES = 12

    fun show(activity: Activity, reconnect: () -> Unit) {
        val text = report(activity)
        Log.i("AminVpnDiag", text.replace("\n", " | "))
        val body = TextView(activity).apply {
            setText(text)
            textSize = 15f
            setTextColor(0xffd3e7ee.toInt())
            setPadding(48, 24, 48, 24)
            textDirection = TextView.TEXT_DIRECTION_LOCALE
        }
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.diag_title)
            .setView(ScrollView(activity).apply { addView(body); isFocusable = true })
            .setPositiveButton(R.string.diag_close, null)
            .setNeutralButton(R.string.diag_reconnect) { _, _ -> reconnect() }
            .setNegativeButton(R.string.diag_legacy) { _, _ -> openLegacy(activity) }
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    fun report(context: Context): String {
        val state = OpenRungStatusStore.uiState.value
        val am = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val pssKb = runCatching {
            am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))[0].totalPss
        }.getOrElse { (Debug.getPss()).toInt() }
        val vpnActive = vpnNetworkActive(context)
        return buildString {
            appendLine("وضعیت: ${state.status.name.lowercase()}")
            if (state.status == ConnectionStatus.CONNECTED) {
                appendLine("سرور: ${state.relayLabel ?: "-"}${state.relayName?.let { " ($it)" } ?: ""}")
            }
            ConnectTimer.lastConnectMillis?.let { appendLine("زمان آخرین اتصال: %.1f ثانیه".format(it / 1000.0)) }
            appendLine("شبکهٔ دستگاه: ${physicalNetworkSummary(context) ?: "بدون اینترنت"}")
            appendLine("تونل VPN اندروید: ${if (vpnActive) "فعال" else "غیرفعال"}")
            state.lastErrorCode?.let { appendLine("کد خطا: $it") }
            state.lastError?.let { appendLine("آخرین خطا: ${redact(it).take(160)}") }
            appendLine("—")
            appendLine("نسخه: ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("دستگاه: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()} · process: ${System.getProperty("os.arch")}")
            appendLine("حافظه: ${memory.availMem / MB} / ${memory.totalMem / MB} MB آزاد${if (memory.lowMemory) " (کم)" else ""} · برنامه: ${pssKb / 1024} MB")
            appendLine("موتور: OpenRung connectcore + sing-box (GPL-3.0)")
            val logs = state.logLines.takeLast(LOG_LINES)
            if (logs.isNotEmpty()) {
                appendLine("—")
                logs.forEach { appendLine(redact(it).take(140)) }
            }
        }.trimEnd()
    }

    fun physicalNetworkSummary(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        val networks = cm.allNetworks
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
            return when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "کابل شبکه (Ethernet)"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "وای‌فای"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "داده موبایل"
                else -> "سایر"
            } + if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) " ✓" else " (تأییدنشده)"
        }
        return null
    }

    private fun vpnNetworkActive(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
    }

    private fun openLegacy(activity: Activity) {
        val status = OpenRungStatusStore.uiState.value.status
        if (status != ConnectionStatus.DISCONNECTED && status != ConnectionStatus.FAILED) {
            AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage(R.string.diag_legacy_busy)
                .setPositiveButton(R.string.diag_close, null)
                .show()
            return
        }
        activity.startActivity(Intent(activity, LegacyCapsuleActivity::class.java))
    }

    private val ipv4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}(?::\d+)?\b""")
    private val ipv6 = Regex("""\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{0,4}\b""")
    private val url = Regex("""\b(?:https?|wss?)://\S+""")
    private val token = Regex("""[A-Za-z0-9_\-+/=]{32,}""")

    /** Masks network addresses, URLs and long opaque strings (tickets, keys, fingerprints). */
    fun redact(line: String): String = line
        .replace(url, "<url>")
        .replace(token, "<redacted>")
        .replace(ipv4, "<ip>")
        .replace(ipv6, "<ip>")

    private const val MB = 1024L * 1024L
}
