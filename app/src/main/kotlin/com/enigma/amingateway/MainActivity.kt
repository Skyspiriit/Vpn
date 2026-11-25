package com.enigma.amingateway

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.openrung.config.AppConfig
import com.openrung.state.ConnectionStatus
import com.openrung.state.OpenRungStatusStore
import com.openrung.state.OpenRungUiState
import com.openrung.vpn.OpenRungVpnService
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Amin VPN: one large button over OpenRung's real VpnService engine. The screen only mirrors
 * [OpenRungStatusStore]; the turquoise "connected" state is shown only after connectcore has
 * verified fresh DNS and pinned HTTPS through the VPN network itself.
 */
class MainActivity : Activity() {
    private lateinit var console: GatewayConsole
    private val scope = MainScope()
    private var observing: Job? = null
    private var localNotice: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OpenRungStatusStore.initialize(applicationContext)
        if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        console = GatewayConsole(
            this,
            ConsoleOptions(
                title = R.string.vpn_title,
                signoff = R.string.vpn_signoff,
                scale = 1.45f,
                secondaryLabel = R.string.vpn_diagnostics,
                onSecondary = { Diagnostics.show(this, ::reconnect) },
            ),
        ) { toggle() }
        setContentView(console)
        console.post { console.focusPower() }
    }

    override fun onStart() {
        super.onStart()
        observing = scope.launch { OpenRungStatusStore.uiState.collect(::render) }
    }

    override fun onStop() {
        observing?.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Many TV remotes send DPAD_CENTER / ENTER / NUMPAD_ENTER; a focused Button already handles
    // them, this only keeps the big button reachable if focus was lost (e.g. after a dialog).
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (currentFocus == null && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            console.focusPower()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun toggle() {
        when (OpenRungStatusStore.uiState.value.status) {
            ConnectionStatus.DISCONNECTED, ConnectionStatus.FAILED -> connect()
            ConnectionStatus.DISCONNECTING -> Unit
            else -> disconnect()
        }
    }

    private fun connect() {
        requestNotificationPermission()
        val consent = try {
            VpnService.prepare(this)
        } catch (error: Exception) {
            Log.w(TAG, "vpn_prepare_failed ${error.javaClass.simpleName}")
            showNotice(R.string.vpn_error_start)
            return
        }
        if (consent == null) {
            start()
            return
        }
        showNotice(R.string.vpn_state_permission)
        try {
            startActivityForResult(consent, VPN_REQUEST)
        } catch (error: Exception) {
            Log.w(TAG, "vpn_consent_unavailable ${error.javaClass.simpleName}")
            showNotice(R.string.vpn_error_start)
        }
    }

    @Deprecated("Activity result API is unavailable on the platform Activity used here.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != VPN_REQUEST) return
        if (resultCode == RESULT_OK) start() else showNotice(R.string.vpn_state_permission_denied)
        Log.i(TAG, "vpn_permission granted=${resultCode == RESULT_OK}")
    }

    private fun start() {
        localNotice = null
        ConnectTimer.started(SystemClock.elapsedRealtime())
        Log.i(TAG, "connect_requested")
        ContextCompat.startForegroundService(this, OpenRungVpnService.connectIntent(this, AppConfig.DEFAULT_BROKER_URL))
    }

    private fun reconnect() {
        if (VpnService.prepare(this) != null) { connect(); return }
        start() // The engine host stops the current run before starting the new one.
    }

    private fun disconnect() {
        localNotice = null
        Log.i(TAG, "disconnect_requested")
        startService(OpenRungVpnService.disconnectIntent(this))
    }

    private fun showNotice(message: Int) {
        localNotice = message
        render(OpenRungStatusStore.uiState.value)
    }

    private fun render(state: OpenRungUiState) {
        ConnectTimer.observe(state.status, SystemClock.elapsedRealtime())
        val notice = localNotice
        val idle = state.status == ConnectionStatus.DISCONNECTED || state.status == ConnectionStatus.FAILED
        val view = when {
            notice != null && idle -> ConsoleState(
                if (notice == R.string.vpn_state_permission) Phase.CONNECTING else Phase.FAILED,
                getString(notice), label = getString(R.string.vpn_button_connect), enabled = true)
            else -> when (state.status) {
                ConnectionStatus.DISCONNECTED -> ConsoleState(Phase.OFF, getString(R.string.vpn_state_off),
                    getString(R.string.vpn_state_off_detail), label = getString(R.string.vpn_button_connect), enabled = true)
                ConnectionStatus.PREPARING -> ConsoleState(Phase.CONNECTING, getString(R.string.vpn_state_preparing),
                    getString(R.string.vpn_state_connecting_detail), label = getString(R.string.vpn_button_cancel), enabled = true)
                ConnectionStatus.CONNECTING -> ConsoleState(Phase.CONNECTING, getString(R.string.vpn_state_connecting),
                    getString(R.string.vpn_state_connecting_detail), label = getString(R.string.vpn_button_cancel), enabled = true)
                ConnectionStatus.CONNECTED -> ConsoleState(Phase.ON, getString(R.string.vpn_state_on),
                    getString(R.string.vpn_state_on_detail, state.relayLabel ?: getString(R.string.relay_location_unknown)),
                    canDisconnect = true, label = getString(R.string.vpn_button_disconnect), enabled = true)
                ConnectionStatus.DISCONNECTING -> ConsoleState(Phase.DISCONNECTING, getString(R.string.vpn_state_disconnecting),
                    label = getString(R.string.vpn_button_disconnecting), enabled = false)
                ConnectionStatus.FAILED -> ConsoleState(Phase.FAILED, getString(R.string.vpn_state_failed),
                    getString(failureMessage(state)), label = getString(R.string.vpn_button_retry), enabled = true)
            }
        }
        console.render(view)
    }

    private fun failureMessage(state: OpenRungUiState): Int = when (state.lastErrorCode) {
        "clock_skew" -> R.string.vpn_error_clock
        "access_denied", "enrollment_refused" -> R.string.vpn_error_access
        else -> if (Diagnostics.physicalNetworkSummary(this) == null) R.string.vpn_error_offline
        else R.string.vpn_state_failed_detail
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
    }

    private companion object {
        const val TAG = "AminVpn"
        const val VPN_REQUEST = 7001
        const val NOTIFICATION_REQUEST = 7002
    }
}

/** Measures tap-to-verified connection time for diagnostics (no identifiers, only durations). */
internal object ConnectTimer {
    private var startedAt = 0L
    @Volatile var lastConnectMillis: Long? = null
        private set
    private var last: ConnectionStatus? = null

    fun started(now: Long) { startedAt = now }

    fun observe(status: ConnectionStatus, now: Long) {
        if (status == last) return
        last = status
        if (status == ConnectionStatus.CONNECTED && startedAt > 0) {
            lastConnectMillis = now - startedAt
            Log.i("AminVpn", "verified_connected ms=${now - startedAt}")
            startedAt = 0
        } else if (status == ConnectionStatus.FAILED) {
            Log.i("AminVpn", "connect_failed")
        }
    }
}
