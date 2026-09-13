package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ApkDetails
import com.example.model.InstalledAppItem
import com.example.util.ApkUtils
import com.example.web.ApkWebServer
import com.example.web.WebServerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class ApkInstallerUiState(
  val selectedApk: ApkDetails? = null,
  val isLoading: Boolean = false,
  val loadingMessage: String = "",
  val statusMessage: String? = null,
  val errorMessage: String? = null,
  val installedApps: List<InstalledAppItem> = emptyList(),
  val searchQuery: String = "",
  val showSystemApps: Boolean = false,
  val activeTab: Int = 0,
  val canInstallPackages: Boolean = true,
  val showPermissionDialog: Boolean = false
) {
  val filteredApps: List<InstalledAppItem>
    get() {
      val base = if (showSystemApps) installedApps else installedApps.filter { !it.isSystemApp }
      if (searchQuery.isBlank()) return base
      val q = searchQuery.trim().lowercase()
      return base.filter {
        it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
      }
    }
}

class ApkInstallerViewModel : ViewModel() {

  private val _uiState = MutableStateFlow(ApkInstallerUiState())
  val uiState: StateFlow<ApkInstallerUiState> = _uiState.asStateFlow()

  private var apkWebServer: ApkWebServer? = null
  private val _webServerState = MutableStateFlow(WebServerState())
  val webServerState: StateFlow<WebServerState> = _webServerState.asStateFlow()

  fun initWebServer(context: Context, onTriggerInstall: (File) -> Unit) {
    if (apkWebServer == null) {
      val server = ApkWebServer(
        context = context.applicationContext,
        onApkLoaded = { apkDetails ->
          _uiState.update {
            it.copy(
              selectedApk = apkDetails,
              statusMessage = "Loaded APK received from Web Hub: ${apkDetails.appName}",
              activeTab = 0
            )
          }
        },
        onTriggerInstall = onTriggerInstall
      )
      apkWebServer = server
      viewModelScope.launch {
        server.state.collect { state ->
          _webServerState.value = state
        }
      }
      server.start(8080)
    }
  }

  fun startWebServer(port: Int = 8080) {
    apkWebServer?.start(port)
  }

  fun stopWebServer() {
    apkWebServer?.stop()
  }

  fun downloadRemoteApk(context: Context, urlString: String) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          loadingMessage = "Downloading APK from web...",
          errorMessage = null,
          statusMessage = null
        )
      }

      val result = withContext(Dispatchers.IO) {
        try {
          val url = URL(urlString)
          val connection = url.openConnection()
          connection.connectTimeout = 15000
          connection.readTimeout = 30000

          val rawName = urlString.substringAfterLast("/").substringBefore("?").ifEmpty { "downloaded_app.apk" }
          val fileName = if (rawName.endsWith(".apk", ignoreCase = true)) rawName else "$rawName.apk"

          val downloadDir = File(context.cacheDir, "remote_apks").apply { mkdirs() }
          val targetFile = File(downloadDir, "${System.currentTimeMillis()}_$fileName")

          connection.getInputStream().use { input ->
            FileOutputStream(targetFile).use { output ->
              input.copyTo(output)
            }
          }

          ApkUtils.parseApkFromFile(context, targetFile, fileName)
        } catch (e: Exception) {
          Result.failure(e)
        }
      }

      result.fold(
        onSuccess = { apk ->
          _uiState.update {
            it.copy(
              isLoading = false,
              selectedApk = apk,
              statusMessage = "Downloaded & verified: ${apk.appName} v${apk.versionName}",
              activeTab = 0
            )
          }
        },
        onFailure = { err ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = "Failed to download APK: ${err.localizedMessage}"
            )
          }
        }
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    apkWebServer?.stop()
  }

  fun checkInstallPermission(context: Context) {
    val canInstall = ApkUtils.canRequestPackageInstalls(context)
    _uiState.update { it.copy(canInstallPackages = canInstall) }
  }

  fun setTab(index: Int) {
    _uiState.update { it.copy(activeTab = index) }
  }

  fun setSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  fun toggleShowSystemApps() {
    _uiState.update { it.copy(showSystemApps = !it.showSystemApps) }
  }

  fun dismissPermissionDialog() {
    _uiState.update { it.copy(showPermissionDialog = false) }
  }

  fun clearMessages() {
    _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
  }

  fun clearSelectedApk() {
    _uiState.update { it.copy(selectedApk = null, statusMessage = null, errorMessage = null) }
  }

  fun selectApkUri(context: Context, uri: Uri) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          loadingMessage = "Analyzing APK archive...",
          errorMessage = null,
          statusMessage = null
        )
      }

      val result = withContext(Dispatchers.IO) {
        ApkUtils.parseApkFromUri(context, uri)
      }

      result.fold(
        onSuccess = { details ->
          _uiState.update {
            it.copy(
              isLoading = false,
              selectedApk = details,
              statusMessage = "Loaded ${details.appName} v${details.versionName}",
              activeTab = 0
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to read APK file"
            )
          }
        }
      )
    }
  }

  fun loadSelfApkForInspection(context: Context) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          loadingMessage = "Loading app APK...",
          errorMessage = null,
          statusMessage = null
        )
      }

      val result = withContext(Dispatchers.IO) {
        val selfFileResult = ApkUtils.getSelfApk(context)
        selfFileResult.mapCatching { file ->
          ApkUtils.parseApkFromFile(context, file, originalFileName = "${context.packageName}.apk").getOrThrow()
        }
      }

      result.fold(
        onSuccess = { details ->
          _uiState.update {
            it.copy(
              isLoading = false,
              selectedApk = details,
              statusMessage = "Loaded sample package: ${details.appName}",
              activeTab = 0
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to inspect self APK"
            )
          }
        }
      )
    }
  }

  fun loadInstalledApps(context: Context) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          loadingMessage = "Scanning installed applications..."
        )
      }

      val apps = withContext(Dispatchers.IO) {
        ApkUtils.getInstalledAppsList(context)
      }

      _uiState.update {
        it.copy(
          isLoading = false,
          installedApps = apps
        )
      }
    }
  }

  fun extractAndInspectApp(context: Context, app: InstalledAppItem) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isLoading = true,
          loadingMessage = "Extracting APK for ${app.appName}..."
        )
      }

      val result = withContext(Dispatchers.IO) {
        val cleanName = "${app.packageName}_v${app.versionName}.apk"
        val extractResult = ApkUtils.extractApkToFile(context, app.apkPath, cleanName)
        extractResult.mapCatching { extractedFile ->
          ApkUtils.parseApkFromFile(context, extractedFile, originalFileName = cleanName).getOrThrow()
        }
      }

      result.fold(
        onSuccess = { details ->
          _uiState.update {
            it.copy(
              isLoading = false,
              selectedApk = details,
              statusMessage = "Extracted & loaded APK for ${details.appName}",
              activeTab = 0
            )
          }
        },
        onFailure = { error ->
          _uiState.update {
            it.copy(
              isLoading = false,
              errorMessage = error.localizedMessage ?: "Failed to extract APK"
            )
          }
        }
      )
    }
  }

  fun installSelectedApk(context: Context, onLaunchIntent: (android.content.Intent) -> Unit) {
    val apk = _uiState.value.selectedApk ?: return
    val apkFile = File(apk.cachedFilePath)

    if (!apkFile.exists()) {
      _uiState.update { it.copy(errorMessage = "APK file no longer found in cache.") }
      return
    }

    val canInstall = ApkUtils.canRequestPackageInstalls(context)
    if (!canInstall) {
      _uiState.update { it.copy(showPermissionDialog = true, canInstallPackages = false) }
      return
    }

    try {
      val intent = ApkUtils.createInstallIntent(context, apkFile)
      onLaunchIntent(intent)
      _uiState.update {
        it.copy(statusMessage = "Opening package installer for ${apk.appName}...")
      }
    } catch (e: Exception) {
      _uiState.update {
        it.copy(errorMessage = "Failed to launch installer: ${e.localizedMessage}")
      }
    }
  }
}
