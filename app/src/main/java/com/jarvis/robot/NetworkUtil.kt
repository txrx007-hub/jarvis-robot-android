package com.jarvis.robot

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

object NetworkUtil {
    private var callback: ConnectivityManager.NetworkCallback? = null

    /**
     * Pins this app's traffic to the WiFi network. Without it Android can send requests over mobile
     * data when the current WiFi has no internet, which is exactly the robot's situation on a
     * hotspot or an offline router, and the robot's address is not reachable that way.
     */
    fun bindToWifi(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        if (callback != null) return
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                cm.bindProcessToNetwork(network)
            }

            override fun onLost(network: Network) {
                cm.bindProcessToNetwork(null)
            }
        }
        try {
            cm.requestNetwork(request, cb)
            callback = cb
        } catch (_: Exception) {
            // Missing permission or no WiFi hardware: the default routing still works on most networks.
        }
    }

    /**
     * Is this address on a home or private network (or Tailscale)? The robot's page is plain HTTP, so the app only talks to such addresses, never to the internet.
     * Accepts 10.x, 172.16-31.x, 192.168.x, 169.254.x, Tailscale 100.64-127.x, localhost, names without a dot and .local / .lan / .ts.net names; an optional :port is ignored.
     */
    fun isHomeAddress(address: String): Boolean {
        val raw = address.trim().lowercase().substringBefore('/')
        val host = if (raw.count { it == ':' } == 1) raw.substringBefore(':') else raw
        if (host.isEmpty()) return false
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) || (a == 100 && b in 64..127) || a == 127
        }
        return !host.contains('.') || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".ts.net")
    }

    /** "192.168.1." for a phone on 192.168.1.x, or null when there is no IPv4 WiFi address. */
    fun subnetPrefix(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        for (network in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val props = cm.getLinkProperties(network) ?: continue
            val v4 = props.linkAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()
                ?: continue
            val parts = v4.hostAddress?.split('.') ?: continue
            if (parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}."
        }
        return null
    }

    /** Looks for robots on the /24 subnet: anything on port 80 answering /api/status with our JSON. */
    suspend fun scanForRobots(prefix: String, onFound: (String) -> Unit): List<String> {
        val client = OkHttpClient.Builder()
            .connectTimeout(700, TimeUnit.MILLISECONDS)
            .readTimeout(900, TimeUnit.MILLISECONDS)
            .callTimeout(1500, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
        val gate = Semaphore(48)
        return withContext(Dispatchers.IO) {
            coroutineScope {
                (1..254).map { n ->
                    async {
                        gate.withPermit {
                            val host = "$prefix$n"
                            if (looksLikeRobot(client, host)) {
                                onFound(host)
                                host
                            } else {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }
    }

    internal fun looksLikeRobot(client: OkHttpClient, host: String): Boolean = try {
        val request = Request.Builder().url("http://$host/api/status").get().build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return false
            val o = JSONObject(r.body?.string() ?: return false)
            o.has("board") && o.has("uptime_s") && o.has("state")
        }
    } catch (_: Exception) {
        false
    }
}
