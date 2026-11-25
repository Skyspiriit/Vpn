package com.enigma.amingateway

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View

class LegacyCapsuleActivity : Activity() {
    private val connection get() = (application as GatewayApplication).vpn
    private lateinit var console: GatewayConsole
    private val render: (ConsoleState) -> Unit = { console.render(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 30) {
            @Suppress("DEPRECATION")
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        console = GatewayConsole(this) { follow(connection.tap(this)) }
        setContentView(console)
    }

    override fun onResume() {
        super.onResume()
        connection.observe(render)
    }

    override fun onPause() {
        connection.stopObserving(render)
        super.onPause()
    }

    private fun follow(step: NextStep) {
        when (step) {
            NextStep.NONE -> Unit
            NextStep.INSTALL -> AlertDialog.Builder(this)
                .setTitle("Install Check Point Capsule VPN")
                .setMessage("The University of Aveiro uses Capsule VPN for sign-in and the secure connection.")
                .setPositiveButton("Open Google Play") { _, _ -> openPlay() }
                .setNegativeButton("Cancel", null)
                .show()
            NextStep.EXISTING_VPN -> AlertDialog.Builder(this)
                .setTitle("A VPN is already active")
                .setMessage("Disconnect the active VPN, then return here to connect to ${CapsuleClient.GATEWAY}.")
                .setPositiveButton("Open Capsule VPN") { _, _ -> openCapsule() }
                .setNeutralButton("VPN settings") { _, _ -> openVpnSettings() }
                .setNegativeButton("Cancel", null)
                .show()
            NextStep.RECOVERY -> AlertDialog.Builder(this)
                .setTitle("Check the Capsule VPN connection")
                .setItems(arrayOf("Retry", "Open Capsule VPN", "Set up the UA site again")) { _, index ->
                    when (index) {
                        0 -> follow(connection.retry(this))
                        1 -> openCapsule()
                        2 -> follow(connection.setUp(this))
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            NextStep.MANUAL -> AlertDialog.Builder(this)
                .setTitle("Continue in Capsule VPN")
                .setMessage("Open Capsule VPN and use ${CapsuleClient.GATEWAY} as the server. " +
                    "You can connect or disconnect directly in Capsule VPN.")
                .setPositiveButton("Open Capsule VPN") { _, _ -> openCapsule() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun openPlay() {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${CapsuleClient.PACKAGE}"))
            .setPackage("com.android.vending")
        val opened = CapsuleClient.launch(this, market) ||
            CapsuleClient.launch(this, Intent(Intent.ACTION_VIEW, Uri.parse(CapsuleClient.PLAY_URL)))
        if (!opened) AlertDialog.Builder(this)
            .setMessage("Find Check Point Capsule VPN by Check Point Software Technologies in Google Play.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openCapsule() {
        if (!CapsuleClient.launch(this, packageManager.getLaunchIntentForPackage(CapsuleClient.PACKAGE))) {
            openVpnSettings()
        }
    }

    private fun openVpnSettings() {
        if (!CapsuleClient.launch(this, Intent(Settings.ACTION_VPN_SETTINGS))) {
            CapsuleClient.launch(this, Intent(Settings.ACTION_SETTINGS))
        }
    }
}
