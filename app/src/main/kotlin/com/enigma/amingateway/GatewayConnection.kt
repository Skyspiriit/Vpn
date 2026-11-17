package com.enigma.amingateway

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import java.util.concurrent.Executors

internal enum class Phase { OFF, CONNECTING, ON, DISCONNECTING, ATTENTION }

internal data class ConsoleState(
    val phase: Phase,
    val status: String,
    val detail: String = "",
    val canDisconnect: Boolean = false
)

internal enum class NextStep { NONE, INSTALL, EXISTING_VPN, RECOVERY, MANUAL }

/** All state is confined to Android's main thread. Capsule owns the tunnel and its credentials. */
internal class GatewayConnection(context: Context) {
    private enum class Operation(val timeout: Long) { CONNECT(90_000), DISCONNECT(15_000) }
    private enum class Notice { SETUP, OFFLINE, TIMEOUT, UNVERIFIED, LINK_LOST, DISCONNECT_TIMEOUT, LEFT }

    private class Attempt(val operation: Operation, val token: String, val deadline: Long) {
        var acknowledged = false
        var candidate: Network? = null
    }

    private class Session(val network: Network) {
        var reachable = true
        var checkedAt = SystemClock.elapsedRealtime()
        var result = ProbeResult.REACHABLE
    }

    private val main = Handler(Looper.getMainLooper())
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val settings = context.getSharedPreferences("capsule_site", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadExecutor()
    private val probe = UniversityProbe(connectivity)
    private val random = SecureRandom()
    private val refreshTask = Runnable { refresh() }
    private var setupSent = settings.getBoolean("setup_sent", false)
    private var observer: ((ConsoleState) -> Unit)? = null
    private var notice: Notice? = null
    private var pending: Attempt? = null
    private var session: Session? = null
    private var blockedNetwork: Network? = null
    private var probing = false
    private var nextProbeAt = 0L
    private var lastTapAt = -1_000L
    private var latest = ConsoleState(Phase.OFF, "The Force is waiting...")

    init {
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) {
                if (blockedNetwork == network) blockedNetwork = null
                if (session?.network == network) {
                    session = null
                    if (pending?.operation != Operation.DISCONNECT) notice = Notice.LINK_LOST
                }
                if (pending?.candidate == network) fail(Notice.UNVERIFIED)
                refresh()
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) = refresh()
            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                if (blocked) blockedNetwork = network else if (blockedNetwork == network) blockedNetwork = null
                if (session?.network == network) {
                    session?.checkedAt = SystemClock.elapsedRealtime() - VERIFICATION_FRESHNESS
                }
                refresh()
            }
        }, main)
    }

    fun observe(listener: (ConsoleState) -> Unit) {
        observer = listener
        refresh()
        listener(latest)
    }

    fun stopObserving(listener: (ConsoleState) -> Unit) {
        if (observer === listener) observer = null
        refresh()
    }

    fun tap(activity: Activity): NextStep {
        refresh()
        val now = SystemClock.elapsedRealtime()
        if (pending != null || now - lastTapAt < 850L) return NextStep.NONE
        lastTapAt = now

        if (!CapsuleClient.installed(activity)) return NextStep.INSTALL
        if (notice == Notice.DISCONNECT_TIMEOUT && session != null) return NextStep.RECOVERY
        if (session != null) return launchAction(activity, Operation.DISCONNECT)
        if (defaultVpn() != null) return NextStep.EXISTING_VPN
        if (notice == Notice.TIMEOUT || notice == Notice.UNVERIFIED) return NextStep.RECOVERY
        if (!hasDefaultNetwork()) {
            notice = Notice.OFFLINE
            refresh()
            return NextStep.NONE
        }
        return if (setupSent) launchAction(activity, Operation.CONNECT) else setUp(activity)
    }

    fun retry(activity: Activity): NextStep {
        notice = null
        lastTapAt = -1_000L
        return tap(activity)
    }

    fun setUp(activity: Activity): NextStep {
        if (!CapsuleClient.installed(activity)) return NextStep.INSTALL
        if (defaultVpn() != null) return NextStep.EXISTING_VPN
        if (!CapsuleClient.launch(activity, CapsuleClient.setup)) return NextStep.MANUAL
        setupSent = true
        settings.edit().putBoolean("setup_sent", true).apply()
        notice = Notice.SETUP
        refresh()
        return NextStep.NONE
    }

    private fun launchAction(activity: Activity, operation: Operation): NextStep {
        val token = Base64.encodeToString(ByteArray(24).also(random::nextBytes),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        pending = Attempt(operation, token, SystemClock.elapsedRealtime() + operation.timeout)
        notice = null
        nextProbeAt = 0L
        refresh()
        if (CapsuleClient.launch(activity, CapsuleClient.action(operation == Operation.CONNECT, token))) {
            return NextStep.NONE
        }
        pending = null
        refresh()
        return NextStep.MANUAL
    }

    fun acknowledge(token: String): Boolean {
        val attempt = pending ?: return false
        if (attempt.token != token || attempt.acknowledged || SystemClock.elapsedRealtime() >= attempt.deadline) {
            return false
        }
        attempt.acknowledged = true
        refresh()
        return true
    }

    private fun defaultVpn(): Network? = connectivity.activeNetwork?.takeIf { network ->
        connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    private fun hasDefaultNetwork(): Boolean = connectivity.activeNetwork != null

    private fun refresh() {
        main.removeCallbacks(refreshTask)
        val now = SystemClock.elapsedRealtime()
        val vpn = defaultVpn()
        val known = session
        val attempt = pending

        if (known != null && known.network != vpn) {
            session = null
            if (attempt?.operation != Operation.DISCONNECT) notice = Notice.LINK_LOST
        }

        if (attempt != null) {
            when (attempt.operation) {
                Operation.CONNECT -> when {
                    attempt.candidate != null && attempt.candidate != vpn -> fail(Notice.UNVERIFIED)
                    now >= attempt.deadline -> fail(if (vpn == null) Notice.TIMEOUT else Notice.UNVERIFIED)
                    else -> {
                        if (vpn != null) attempt.candidate = vpn
                        if (attempt.acknowledged && vpn != null && vpn != blockedNetwork && now >= nextProbeAt) {
                            startProbe(vpn, attempt, null)
                        }
                    }
                }
                Operation.DISCONNECT -> when {
                    vpn == null && (attempt.acknowledged || now >= attempt.deadline) -> {
                        pending = null
                        notice = Notice.LEFT
                    }
                    now >= attempt.deadline -> fail(Notice.DISCONNECT_TIMEOUT)
                }
            }
        }

        val current = session
        if (pending == null && current != null && current.network != blockedNetwork && observer != null &&
            now - current.checkedAt >= RECHECK_INTERVAL
        ) {
            startProbe(current.network, null, current)
        }
        publish(state(vpn))

        val ongoing = pending
        if (ongoing != null) {
            val retryIn = if (!probing && ongoing.acknowledged && vpn != null && vpn != blockedNetwork && ongoing.operation == Operation.CONNECT)
                (nextProbeAt - now).coerceAtLeast(1L) else Long.MAX_VALUE
            main.postDelayed(refreshTask, minOf(ongoing.deadline - now, retryIn).coerceAtLeast(1L))
        } else if (current != null && current.network != blockedNetwork && observer != null) {
            val delay = (if (probing) VERIFICATION_FRESHNESS else RECHECK_INTERVAL) - (now - current.checkedAt)
            if (delay > 0L) main.postDelayed(refreshTask, delay)
        }
    }

    private fun startProbe(network: Network, attempt: Attempt?, known: Session?) {
        if (probing) return
        probing = true
        worker.execute {
            val result = probe.check(network)
            main.post {
                probing = false
                if (result != ProbeResult.REACHABLE) Log.i(TAG, "probe_${result.name.lowercase()}")
                if (network == defaultVpn() && network != blockedNetwork) {
                    if (attempt != null && pending === attempt && SystemClock.elapsedRealtime() < attempt.deadline) {
                        if (result == ProbeResult.REACHABLE) {
                            session = Session(network)
                            pending = null
                            notice = null
                        } else {
                            nextProbeAt = SystemClock.elapsedRealtime() + 5_000
                        }
                    } else if (known != null && session === known && pending == null) {
                        known.checkedAt = SystemClock.elapsedRealtime()
                        known.reachable = result == ProbeResult.REACHABLE
                        known.result = result
                    }
                }
                refresh()
            }
        }
    }

    private fun fail(reason: Notice) {
        Log.i(TAG, "attempt_${reason.name.lowercase()}")
        pending = null
        notice = reason
    }

    private fun state(vpn: Network?): ConsoleState {
        val attempt = pending
        if (attempt?.operation == Operation.CONNECT) {
            return ConsoleState(Phase.CONNECTING, "Establishing hyperspace connection...",
                if (attempt.acknowledged) "Verifying the route to the university." else "Complete any sign-in prompts in Capsule VPN.")
        }
        if (attempt?.operation == Operation.DISCONNECT) {
            return ConsoleState(Phase.DISCONNECTING, "Leaving hyperspace...", canDisconnect = true)
        }
        val current = session
        if (current != null) {
            if (notice == Notice.DISCONNECT_TIMEOUT) {
                return ConsoleState(Phase.ATTENTION, "Hyperspace exit timed out.",
                    "Retry or disconnect in Capsule VPN.", canDisconnect = true)
            }
            if (current.reachable && current.network != blockedNetwork &&
                SystemClock.elapsedRealtime() - current.checkedAt < VERIFICATION_FRESHNESS
            ) {
                return ConsoleState(Phase.ON, "You joined the Force successfully", canDisconnect = true)
            }
            return ConsoleState(Phase.ATTENTION,
                if (probing) "Checking the university route..." else "The university route is out of reach.",
                if (current.result == ProbeResult.TLS_FAILED) "The secure connection check failed." else "Check the connection in Capsule VPN.",
                canDisconnect = true)
        }
        if (vpn != null) return ConsoleState(Phase.ATTENTION, "VPN connection unverified.", "Open Capsule VPN to check or disconnect it.")
        return when (notice) {
            Notice.SETUP -> ConsoleState(Phase.OFF, "The Force is waiting...", "Finish site setup in Capsule VPN, then tap to connect.")
            Notice.OFFLINE -> ConsoleState(Phase.ATTENTION, "Lost in hyperspace. Check your internet connection.")
            Notice.TIMEOUT -> ConsoleState(Phase.ATTENTION, "Hyperspace jump failed. Try again.", "Check the connection or sign-in message in Capsule VPN.")
            Notice.UNVERIFIED -> ConsoleState(Phase.ATTENTION, "The university route could not be verified.", "Tap to retry or check Capsule VPN.")
            Notice.LINK_LOST -> ConsoleState(Phase.ATTENTION, "The hyperspace connection was lost.")
            Notice.DISCONNECT_TIMEOUT -> ConsoleState(Phase.ATTENTION, "Hyperspace exit could not be confirmed.", "Check Capsule VPN.")
            Notice.LEFT -> ConsoleState(Phase.OFF, "The Force will be with you. Always.")
            null -> ConsoleState(Phase.OFF, "The Force is waiting...",
                if (setupSent) "" else "First-time setup opens Check Point Capsule VPN.")
        }
    }

    private fun publish(state: ConsoleState) {
        if (state == latest) return
        latest = state
        observer?.invoke(state)
    }

    private companion object {
        const val TAG = "AminGateway"
        const val RECHECK_INTERVAL = 45_000L
        const val VERIFICATION_FRESHNESS = 55_000L
    }
}
