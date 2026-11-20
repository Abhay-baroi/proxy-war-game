package com.example.network

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    fun getLocalIpv4Address(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val preferred = interfaces
                .filter { it.isUp && !it.isLoopback }
                .sortedByDescending { iface ->
                    val name = iface.name.lowercase()
                    when {
                        name.startsWith("wlan") -> 3
                        name.startsWith("ap") -> 2
                        name.startsWith("eth") -> 1
                        else -> 0
                    }
                }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }

            preferred?.hostAddress ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
