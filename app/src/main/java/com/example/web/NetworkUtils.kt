package com.example.web

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

  fun getDeviceIpAddress(context: Context): String {
    try {
      // 1. Try WifiManager first if on Wi-Fi
      val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      val ip = wifiManager?.connectionInfo?.ipAddress ?: 0
      if (ip != 0) {
        val formattedIp = String.format(
          "%d.%d.%d.%d",
          ip and 0xff,
          ip shr 8 and 0xff,
          ip shr 16 and 0xff,
          ip shr 24 and 0xff
        )
        if (formattedIp != "0.0.0.0") {
          return formattedIp
        }
      }

      // 2. Iterate network interfaces for non-loopback IPv4
      val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
      for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue
        val addrs = Collections.list(intf.inetAddresses)
        for (addr in addrs) {
          if (!addr.isLoopbackAddress && addr is Inet4Address) {
            val hostAddress = addr.hostAddress ?: ""
            if (hostAddress.isNotBlank()) {
              return hostAddress
            }
          }
        }
      }
    } catch (_: Exception) {
      // Ignore and fallback
    }
    return "127.0.0.1"
  }
}
