package com.enigma.amingateway

import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal enum class ProbeResult {
    REACHABLE, NO_CAMPUS_ROUTE, DNS_UNAVAILABLE, TIMEOUT, TLS_FAILED, UNREACHABLE
}

internal class UniversityProbe(private val connectivity: ConnectivityManager) {
    // A UA-hosted HTTPS service. Reachability here establishes a functioning route to UA.
    private val host = "my.ua.pt"

    fun check(network: Network): ProbeResult = try {
        val addresses = network.getAllByName(host)
        val link = connectivity.getLinkProperties(network)
        val address = addresses.firstOrNull { candidate ->
            val route = link?.routes?.filter { it.matches(candidate) }
                ?.maxByOrNull { it.destination.prefixLength }
            route != null && route.`interface` == link.interfaceName &&
                (Build.VERSION.SDK_INT < 33 || route.type == android.net.RouteInfo.RTN_UNICAST)
        }
        if (address == null) {
            ProbeResult.NO_CAMPUS_ROUTE
        } else {
            val tls = SSLSocketFactory.getDefault() as SSLSocketFactory
            network.socketFactory.createSocket().use { socket ->
                socket.connect(InetSocketAddress(address, 443), 4_000)
                socket.soTimeout = 4_000
                (tls.createSocket(socket, host, 443, true) as SSLSocket).use { secure ->
                    secure.sslParameters = secure.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                    secure.startHandshake()
                }
            }
            ProbeResult.REACHABLE
        }
    } catch (_: UnknownHostException) {
        ProbeResult.DNS_UNAVAILABLE
    } catch (_: SocketTimeoutException) {
        ProbeResult.TIMEOUT
    } catch (_: SSLException) {
        ProbeResult.TLS_FAILED
    } catch (_: IOException) {
        ProbeResult.UNREACHABLE
    } catch (_: SecurityException) {
        ProbeResult.UNREACHABLE
    }
}
