package com.enigma.amingateway

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log

internal object CapsuleClient {
    const val PACKAGE = "com.checkpoint.VPN"
    const val GATEWAY = "go.ua.pt"
    const val SITE = "Amin-Gateway-UA"
    const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PACKAGE"

    val setup = request("v1&name=$SITE&host=$GATEWAY&auth=username&tun=kmp&remoteActions=yes")

    fun action(connect: Boolean, token: String): Intent {
        val command = if (connect) "connect" else "disconnect"
        val callback = Uri.encode("amingateway://capsule/result/$token")
        return request("$command&siteName=$SITE&url=$callback")
    }

    private fun request(query: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse("cpvpn:///?$query")).setPackage(PACKAGE)

    @Suppress("DEPRECATION")
    fun installed(activity: Activity): Boolean = try {
        activity.packageManager.getApplicationInfo(PACKAGE, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun launch(activity: Activity, request: Intent?): Boolean {
        if (request == null) return false
        return try {
            activity.startActivity(request)
            true
        } catch (_: ActivityNotFoundException) {
            Log.i("AminGateway", "capsule_activity_unavailable")
            false
        } catch (_: SecurityException) {
            Log.i("AminGateway", "capsule_activity_blocked")
            false
        }
    }
}

class GatewayApplication : Application() {
    internal val vpn by lazy { GatewayConnection(this) }
}

class CapsuleReturnActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        if (intent.action == Intent.ACTION_VIEW && uri != null && uri.scheme == "amingateway" &&
            uri.encodedAuthority == "capsule" && uri.encodedQuery == null && uri.encodedFragment == null &&
            uri.pathSegments.size == 2 && uri.pathSegments.first() == "result" &&
            (application as GatewayApplication).vpn.acknowledge(uri.pathSegments.last())
        ) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ))
        }
        finish()
    }
}
