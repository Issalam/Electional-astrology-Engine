package com.example.web

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.model.ApkDetails
import com.example.util.ApkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder

data class WebServerState(
  val isRunning: Boolean = false,
  val port: Int = 8080,
  val hostIp: String = "127.0.0.1",
  val totalRequests: Int = 0,
  val lastMessage: String? = null
) {
  val serverUrl: String get() = "http://$hostIp:$port"
  val loopbackUrl: String get() = "http://127.0.0.1:$port"
}

class ApkWebServer(
  private val context: Context,
  private val onApkLoaded: (ApkDetails) -> Unit,
  private val onTriggerInstall: (File) -> Unit
) {

  private var serverSocket: ServerSocket? = null
  private var serverJob: Job? = null
  private val scope = CoroutineScope(Dispatchers.IO)

  private val _state = MutableStateFlow(WebServerState())
  val state: StateFlow<WebServerState> = _state.asStateFlow()

  @Synchronized
  fun start(requestedPort: Int = 8080): Boolean {
    if (_state.value.isRunning) return true

    try {
      val socket = ServerSocket(requestedPort)
      serverSocket = socket
      val activePort = socket.localPort
      val ip = NetworkUtils.getDeviceIpAddress(context)

      _state.value = WebServerState(
        isRunning = true,
        port = activePort,
        hostIp = ip,
        lastMessage = "Server listening on port $activePort"
      )

      serverJob = scope.launch {
        while (serverSocket?.isClosed == false) {
          try {
            val client = socket.accept()
            launch(Dispatchers.IO) {
              handleClientConnection(client)
            }
          } catch (_: Exception) {
            // Socket closed or error
            break
          }
        }
      }
      return true
    } catch (e: Exception) {
      _state.value = _state.value.copy(
        isRunning = false,
        lastMessage = "Failed to start server: ${e.localizedMessage}"
      )
      return false
    }
  }

  @Synchronized
  fun stop() {
    try {
      serverSocket?.close()
    } catch (_: Exception) {}
    serverJob?.cancel()
    serverSocket = null
    _state.value = _state.value.copy(
      isRunning = false,
      lastMessage = "Server stopped"
    )
  }

  private fun handleClientConnection(socket: Socket) {
    _state.value = _state.value.copy(totalRequests = _state.value.totalRequests + 1)
    try {
      val inputStream = BufferedInputStream(socket.getInputStream())
      val outputStream = BufferedOutputStream(socket.getOutputStream())

      // Read HTTP Request line
      val requestLine = readLine(inputStream) ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      val method = parts[0].uppercase()
      val fullPath = parts[1]

      val headers = mutableMapOf<String, String>()
      var headerLine: String?
      while (true) {
        headerLine = readLine(inputStream)
        if (headerLine.isNullOrBlank()) break
        val colonIdx = headerLine.indexOf(':')
        if (colonIdx != -1) {
          val key = headerLine.substring(0, colonIdx).trim().lowercase()
          val value = headerLine.substring(colonIdx + 1).trim()
          headers[key] = value
        }
      }

      val uriPath = if (fullPath.contains("?")) fullPath.substringBefore("?") else fullPath
      val queryString = if (fullPath.contains("?")) fullPath.substringAfter("?") else ""

      when {
        // OPTIONS Pre-flight for CORS
        method == "OPTIONS" -> {
          sendResponse(outputStream, 204, "No Content", "text/plain", ByteArray(0))
        }

        // Web Dashboard HTML
        method == "GET" && (uriPath == "/" || uriPath == "/index.html") -> {
          val htmlBytes = ApkWebDashboardHtml.getHtml().toByteArray(Charsets.UTF_8)
          sendResponse(outputStream, 200, "OK", "text/html; charset=utf-8", htmlBytes)
        }

        // API: Status
        method == "GET" && uriPath == "/api/status" -> {
          val json = JSONObject().apply {
            put("status", "running")
            put("deviceModel", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
            put("osVersion", Build.VERSION.RELEASE)
            put("sdkLevel", Build.VERSION.SDK_INT)
            put("canInstallPackages", ApkUtils.canRequestPackageInstalls(context))
            put("installedAppCount", ApkUtils.getInstalledAppsList(context).size)
            put("port", _state.value.port)
            put("hostIp", _state.value.hostIp)
          }
          val bytes = json.toString().toByteArray(Charsets.UTF_8)
          sendResponse(outputStream, 200, "OK", "application/json", bytes)
        }

        // API: Installed Apps List
        method == "GET" && uriPath == "/api/apps" -> {
          val apps = ApkUtils.getInstalledAppsList(context)
          val jsonArr = JSONArray()
          for (app in apps) {
            val appObj = JSONObject().apply {
              put("appName", app.appName)
              put("packageName", app.packageName)
              put("versionName", app.versionName)
              put("versionCode", app.versionCode)
              put("isSystemApp", app.isSystemApp)
              put("apkSize", app.apkSize)
              put("formattedSize", app.formattedSize)
            }
            jsonArr.put(appObj)
          }
          val bytes = jsonArr.toString().toByteArray(Charsets.UTF_8)
          sendResponse(outputStream, 200, "OK", "application/json", bytes)
        }

        // API: Download Installed APK
        method == "GET" && uriPath == "/api/download-apk" -> {
          val queryParams = parseQueryParams(queryString)
          val pkg = queryParams["package"]
          if (pkg.isNullOrBlank()) {
            sendResponse(outputStream, 400, "Bad Request", "text/plain", "Missing package parameter".toByteArray())
          } else {
            handleDownloadApk(pkg, outputStream)
          }
        }

        // API: Upload APK
        method == "POST" && uriPath == "/api/upload" -> {
          val contentLength = headers["content-length"]?.toLongOrNull() ?: 0L
          val queryParams = parseQueryParams(queryString)
          val fileName = queryParams["filename"] ?: "uploaded_app.apk"
          handleApkUpload(inputStream, contentLength, fileName, outputStream)
        }

        // API: Trigger Installation on Phone
        method == "POST" && uriPath == "/api/install" -> {
          val contentLength = headers["content-length"]?.toInt() ?: 0
          val body = readBodyString(inputStream, contentLength)
          val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
          val path = json.optString("filePath", "")
          if (path.isNotBlank() && File(path).exists()) {
            onTriggerInstall(File(path))
            val res = JSONObject().put("success", true).put("message", "Installer initiated on device").toString()
            sendResponse(outputStream, 200, "OK", "application/json", res.toByteArray())
          } else {
            val res = JSONObject().put("success", false).put("error", "Invalid or missing file path").toString()
            sendResponse(outputStream, 400, "Bad Request", "application/json", res.toByteArray())
          }
        }

        // API: Remote URL Download
        method == "POST" && uriPath == "/api/download-url" -> {
          val contentLength = headers["content-length"]?.toInt() ?: 0
          val body = readBodyString(inputStream, contentLength)
          val json = try { JSONObject(body) } catch (_: Exception) { JSONObject() }
          val url = json.optString("url", "")
          if (url.isNotBlank()) {
            handleRemoteUrlDownload(url, outputStream)
          } else {
            val res = JSONObject().put("success", false).put("error", "URL cannot be empty").toString()
            sendResponse(outputStream, 400, "Bad Request", "application/json", res.toByteArray())
          }
        }

        else -> {
          val notFound = "<html><body><h1>404 Not Found</h1></body></html>".toByteArray()
          sendResponse(outputStream, 404, "Not Found", "text/html", notFound)
        }
      }
    } catch (_: Exception) {
      // Socket exception
    } finally {
      try {
        socket.close()
      } catch (_: Exception) {}
    }
  }

  private fun handleDownloadApk(packageName: String, outputStream: OutputStream) {
    try {
      val pm = context.packageManager
      val pkgInfo = pm.getPackageInfo(packageName, 0)
      val appInfo = pkgInfo.applicationInfo ?: throw Exception("App info not found")
      val sourceDir = appInfo.sourceDir ?: throw Exception("APK source path not found")
      val apkFile = File(sourceDir)
      if (!apkFile.exists() || !apkFile.canRead()) {
        sendResponse(outputStream, 404, "Not Found", "text/plain", "APK file not readable".toByteArray())
        return
      }

      val headerText = StringBuilder().apply {
        append("HTTP/1.1 200 OK\r\n")
        append("Content-Type: application/vnd.android.package-archive\r\n")
        append("Content-Length: ${apkFile.length()}\r\n")
        append("Content-Disposition: attachment; filename=\"${packageName}.apk\"\r\n")
        append("Access-Control-Allow-Origin: *\r\n")
        append("Connection: close\r\n\r\n")
      }.toString()

      outputStream.write(headerText.toByteArray(Charsets.UTF_8))

      apkFile.inputStream().use { input ->
        val buffer = ByteArray(32768)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
          outputStream.write(buffer, 0, read)
        }
      }
      outputStream.flush()
    } catch (e: Exception) {
      sendResponse(outputStream, 500, "Error", "text/plain", (e.localizedMessage ?: "Download error").toByteArray())
    }
  }

  private fun handleApkUpload(
    inputStream: InputStream,
    contentLength: Long,
    originalFileName: String,
    outputStream: OutputStream
  ) {
    try {
      val uploadDir = File(context.cacheDir, "web_uploads").apply { mkdirs() }
      val cleanFileName = if (originalFileName.endsWith(".apk", ignoreCase = true)) {
        originalFileName
      } else {
        "$originalFileName.apk"
      }
      val targetFile = File(uploadDir, "${System.currentTimeMillis()}_$cleanFileName")

      FileOutputStream(targetFile).use { fos ->
        val buffer = ByteArray(32768)
        var totalRead = 0L
        while (totalRead < contentLength) {
          val toRead = (contentLength - totalRead).coerceAtMost(buffer.size.toLong()).toInt()
          val r = inputStream.read(buffer, 0, toRead)
          if (r == -1) break
          fos.write(buffer, 0, r)
          totalRead += r
        }
      }

      val parseResult = ApkUtils.parseApkFromFile(context, targetFile, cleanFileName)
      parseResult.fold(
        onSuccess = { apkDetails ->
          onApkLoaded(apkDetails)
          val json = JSONObject().apply {
            put("success", true)
            put("appName", apkDetails.appName)
            put("packageName", apkDetails.packageName)
            put("versionName", apkDetails.versionName)
            put("versionCode", apkDetails.versionCode)
            put("minSdkVersion", apkDetails.minSdkVersion)
            put("targetSdkVersion", apkDetails.targetSdkVersion)
            put("formattedSize", apkDetails.formattedSize)
            put("cachedFilePath", apkDetails.cachedFilePath)
            val permArr = JSONArray()
            apkDetails.permissions.forEach { permArr.put(it) }
            put("permissions", permArr)
          }
          sendResponse(outputStream, 200, "OK", "application/json", json.toString().toByteArray(Charsets.UTF_8))
        },
        onFailure = { error ->
          val json = JSONObject().apply {
            put("success", false)
            put("error", error.localizedMessage ?: "Invalid APK")
          }
          sendResponse(outputStream, 400, "Bad Request", "application/json", json.toString().toByteArray(Charsets.UTF_8))
        }
      )
    } catch (e: Exception) {
      val json = JSONObject().apply {
        put("success", false)
        put("error", e.localizedMessage ?: "Upload failed")
      }
      sendResponse(outputStream, 500, "Error", "application/json", json.toString().toByteArray(Charsets.UTF_8))
    }
  }

  private fun handleRemoteUrlDownload(remoteUrl: String, outputStream: OutputStream) {
    try {
      val connection = URL(remoteUrl).openConnection()
      connection.connectTimeout = 15000
      connection.readTimeout = 30000

      val rawFileName = remoteUrl.substringAfterLast("/").substringBefore("?").ifEmpty { "download.apk" }
      val cleanFileName = if (rawFileName.endsWith(".apk", ignoreCase = true)) rawFileName else "$rawFileName.apk"

      val downloadDir = File(context.cacheDir, "remote_apks").apply { mkdirs() }
      val targetFile = File(downloadDir, "${System.currentTimeMillis()}_$cleanFileName")

      connection.getInputStream().use { input ->
        FileOutputStream(targetFile).use { output ->
          input.copyTo(output)
        }
      }

      val parseResult = ApkUtils.parseApkFromFile(context, targetFile, cleanFileName)
      parseResult.fold(
        onSuccess = { apk ->
          onApkLoaded(apk)
          val apkJson = JSONObject().apply {
            put("appName", apk.appName)
            put("packageName", apk.packageName)
            put("versionName", apk.versionName)
            put("versionCode", apk.versionCode)
            put("minSdkVersion", apk.minSdkVersion)
            put("targetSdkVersion", apk.targetSdkVersion)
            put("formattedSize", apk.formattedSize)
            put("cachedFilePath", apk.cachedFilePath)
            val permArr = JSONArray()
            apk.permissions.forEach { permArr.put(it) }
            put("permissions", permArr)
          }
          val res = JSONObject().apply {
            put("success", true)
            put("apk", apkJson)
          }
          sendResponse(outputStream, 200, "OK", "application/json", res.toString().toByteArray())
        },
        onFailure = { err ->
          val res = JSONObject().apply {
            put("success", false)
            put("error", "Downloaded file is not a valid APK: ${err.localizedMessage}")
          }
          sendResponse(outputStream, 400, "Bad Request", "application/json", res.toString().toByteArray())
        }
      )
    } catch (e: Exception) {
      val res = JSONObject().apply {
        put("success", false)
        put("error", "Download failed: ${e.localizedMessage}")
      }
      sendResponse(outputStream, 500, "Internal Error", "application/json", res.toString().toByteArray())
    }
  }

  private fun readLine(inputStream: InputStream): String? {
    val sb = StringBuilder()
    var prev = 0
    while (true) {
      val ch = inputStream.read()
      if (ch == -1) {
        return if (sb.isNotEmpty()) sb.toString() else null
      }
      if (ch == '\n'.code) {
        if (prev == '\r'.code && sb.isNotEmpty()) {
          sb.deleteCharAt(sb.length - 1)
        }
        return sb.toString()
      }
      sb.append(ch.toChar())
      prev = ch
    }
  }

  private fun readBodyString(inputStream: InputStream, length: Int): String {
    if (length <= 0) return ""
    val bytes = ByteArray(length)
    var read = 0
    while (read < length) {
      val r = inputStream.read(bytes, read, length - read)
      if (r == -1) break
      read += r
    }
    return String(bytes, 0, read, Charsets.UTF_8)
  }

  private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    val map = mutableMapOf<String, String>()
    query.split("&").forEach { pair ->
      val parts = pair.split("=")
      if (parts.size == 2) {
        val key = URLDecoder.decode(parts[0], "UTF-8")
        val value = URLDecoder.decode(parts[1], "UTF-8")
        map[key] = value
      }
    }
    return map
  }

  private fun sendResponse(
    outputStream: OutputStream,
    statusCode: Int,
    statusText: String,
    contentType: String,
    body: ByteArray
  ) {
    val header = StringBuilder().apply {
      append("HTTP/1.1 $statusCode $statusText\r\n")
      append("Content-Type: $contentType\r\n")
      append("Content-Length: ${body.size}\r\n")
      append("Access-Control-Allow-Origin: *\r\n")
      append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
      append("Access-Control-Allow-Headers: Content-Type, Content-Length\r\n")
      append("Connection: close\r\n\r\n")
    }.toString()

    outputStream.write(header.toByteArray(Charsets.UTF_8))
    outputStream.write(body)
    outputStream.flush()
  }
}
