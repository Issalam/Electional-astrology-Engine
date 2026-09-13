package com.example.ui

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ApkDetails
import com.example.model.InstallStatus
import com.example.model.InstalledAppItem
import com.example.util.ApkUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkInstallerScreen(
  viewModel: ApkInstallerViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val uiState by viewModel.uiState.collectAsState()
  val webServerState by viewModel.webServerState.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }

  // File picker launcher for .apk files
  val apkPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    if (uri != null) {
      viewModel.selectApkUri(context, uri)
    }
  }

  // Refresh permission check & start embedded web server on launch
  LaunchedEffect(Unit) {
    viewModel.checkInstallPermission(context)
    viewModel.initWebServer(context) { file ->
      try {
        val intent = ApkUtils.createInstallIntent(context, file)
        context.startActivity(intent)
      } catch (e: Exception) {
        // Handled via state or toast
      }
    }
  }

  // Handle messages
  LaunchedEffect(uiState.errorMessage) {
    uiState.errorMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearMessages()
    }
  }

  LaunchedEffect(uiState.statusMessage) {
    uiState.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearMessages()
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Android,
              contentDescription = "App Icon",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = "APK Installer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Package Manager & Sideload Utility",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        },
        actions = {
          // Web Server quick status badge
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (webServerState.isRunning) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
              .padding(end = 6.dp)
              .clip(RoundedCornerShape(12.dp))
              .clickable { viewModel.setTab(2) }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(if (webServerState.isRunning) Color(0xFF22C55E) else Color(0xFFEF4444))
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = if (webServerState.isRunning) ":${webServerState.port}" else "Web Off",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
              )
            }
          }

          if (uiState.selectedApk != null) {
            IconButton(
              onClick = { viewModel.clearSelectedApk() },
              modifier = Modifier.testTag("clear_apk_button")
            ) {
              Icon(Icons.Default.Close, contentDescription = "Clear selected APK")
            }
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      )
    }
  ) { paddingValues ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // Permission warning banner if install permission is missing
      AnimatedVisibility(visible = !uiState.canInstallPackages) {
        PermissionBanner(
          onGrantClick = { ApkUtils.openUnknownAppSourcesSettings(context) }
        )
      }

      // Tab navigation
      TabRow(
        selectedTabIndex = uiState.activeTab,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
      ) {
        Tab(
          selected = uiState.activeTab == 0,
          onClick = { viewModel.setTab(0) },
          text = { Text("Install") },
          icon = { Icon(Icons.Default.FileDownload, contentDescription = "Install tab") },
          modifier = Modifier.testTag("tab_install")
        )
        Tab(
          selected = uiState.activeTab == 1,
          onClick = {
            viewModel.setTab(1)
            if (uiState.installedApps.isEmpty()) {
              viewModel.loadInstalledApps(context)
            }
          },
          text = { Text("Extractor") },
          icon = { Icon(Icons.Default.Apps, contentDescription = "Extractor tab") },
          modifier = Modifier.testTag("tab_extractor")
        )
        Tab(
          selected = uiState.activeTab == 2,
          onClick = { viewModel.setTab(2) },
          text = { Text("Web App") },
          icon = { Icon(Icons.Default.Language, contentDescription = "Web App tab") },
          modifier = Modifier.testTag("tab_web_app")
        )
        Tab(
          selected = uiState.activeTab == 3,
          onClick = { viewModel.setTab(3) },
          text = { Text("System") },
          icon = { Icon(Icons.Default.Info, contentDescription = "Info tab") },
          modifier = Modifier.testTag("tab_info")
        )
      }

      // Content by Tab
      Box(modifier = Modifier.weight(1f)) {
        when (uiState.activeTab) {
          0 -> InstallApkTabContent(
            uiState = uiState,
            onPickApk = { apkPickerLauncher.launch("*/*") },
            onLoadSampleApk = { viewModel.loadSelfApkForInspection(context) },
            onInstallApk = {
              viewModel.installSelectedApk(context) { intent ->
                context.startActivity(intent)
              }
            }
          )

          1 -> AppExtractorTabContent(
            uiState = uiState,
            onSearchChange = { viewModel.setSearchQuery(it) },
            onToggleSystemApps = { viewModel.toggleShowSystemApps() },
            onRefresh = { viewModel.loadInstalledApps(context) },
            onExtractApp = { app -> viewModel.extractAndInspectApp(context, app) }
          )

          2 -> WebDashboardContent(
            webServerState = webServerState,
            onStartServer = { viewModel.startWebServer() },
            onStopServer = { viewModel.stopWebServer() },
            onDownloadRemoteUrl = { url -> viewModel.downloadRemoteApk(context, url) }
          )

          3 -> SystemInfoTabContent(
            uiState = uiState,
            onCheckPermission = {
              viewModel.checkInstallPermission(context)
              ApkUtils.openUnknownAppSourcesSettings(context)
            }
          )
        }

        // Loading overlay
        if (uiState.isLoading) {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
          ) {
            Column(
              modifier = Modifier.fillMaxSize(),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              CircularProgressIndicator()
              Spacer(modifier = Modifier.height(16.dp))
              Text(
                text = uiState.loadingMessage.ifEmpty { "Processing..." },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }
    }
  }

  // Permission explanation dialog
  if (uiState.showPermissionDialog) {
    AlertDialog(
      onDismissRequest = { viewModel.dismissPermissionDialog() },
      icon = {
        Icon(
          Icons.Default.Security,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(36.dp)
        )
      },
      title = { Text("Install Permission Required") },
      text = {
        Text(
          "Android requires explicit permission for APK Installer to sideload applications. " +
            "Please allow 'Install unknown apps' for this application in System Settings, then return to complete installation."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.dismissPermissionDialog()
            ApkUtils.openUnknownAppSourcesSettings(context)
          },
          modifier = Modifier.testTag("dialog_settings_button")
        ) {
          Text("Open Settings")
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.dismissPermissionDialog() }) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun PermissionBanner(onGrantClick: () -> Unit) {
  Card(
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer
    ),
    shape = RoundedCornerShape(0.dp),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          Icons.Default.Warning,
          contentDescription = "Warning",
          tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Install Unknown Apps permission needed to sideload APKs.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onErrorContainer
        )
      }
      Button(
        onClick = onGrantClick,
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.error
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
      ) {
        Text("Enable", style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

@Composable
private fun InstallApkTabContent(
  uiState: ApkInstallerUiState,
  onPickApk: () -> Unit,
  onLoadSampleApk: () -> Unit,
  onInstallApk: () -> Unit
) {
  val apk = uiState.selectedApk

  if (apk == null) {
    // Empty state / File chooser prompt
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      item {
        Card(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ),
          shape = RoundedCornerShape(20.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
              )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Select an APK Package",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = "Browse your device storage to analyze, verify permissions, and install any Android application package (.apk).",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
              onClick = onPickApk,
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("pick_apk_button"),
              shape = RoundedCornerShape(14.dp)
            ) {
              Icon(Icons.Default.FolderOpen, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Choose APK from Storage", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
              onClick = onLoadSampleApk,
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sample_apk_button"),
              shape = RoundedCornerShape(14.dp)
            ) {
              Icon(Icons.Default.Android, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Inspect Self / Sample Package")
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly
        ) {
          FeatureHighlightBadge(
            icon = Icons.Default.Security,
            label = "Permission Audit"
          )
          FeatureHighlightBadge(
            icon = Icons.Default.SystemUpdate,
            label = "Version Diffing"
          )
          FeatureHighlightBadge(
            icon = Icons.Default.Apps,
            label = "Clean Sideload"
          )
        }
      }
    }
  } else {
    // Rich APK Details and Install Action Screen
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header Card: App Icon, Name, Version, Status
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
          ),
          shape = RoundedCornerShape(18.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth()
            ) {
              if (apk.iconBitmap != null) {
                Image(
                  bitmap = apk.iconBitmap.asImageBitmap(),
                  contentDescription = apk.appName,
                  modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                )
              } else {
                Box(
                  modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    Icons.Default.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.width(16.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = apk.appName,
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Text(
                  text = apk.packageName,
                  style = MaterialTheme.typography.bodySmall,
                  fontFamily = FontFamily.Monospace,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "v${apk.versionName}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    text = " (${apk.versionCode})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            // Installation status chip
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              StatusBadge(status = apk.installStatus)
              Text(
                text = apk.formattedSize,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
              )
            }

            if (apk.isAlreadyInstalled) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Currently installed: v${apk.installedVersionName ?: "Unknown"} (build ${apk.installedVersionCode ?: 0})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // Primary Install Action Button
      item {
        Button(
          onClick = onInstallApk,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("install_apk_button"),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Default.FileDownload, contentDescription = null)
          Spacer(modifier = Modifier.width(10.dp))
          val btnLabel = when (apk.installStatus) {
            InstallStatus.NEW_INSTALL -> "Install Application"
            InstallStatus.UPDATE -> "Update Application"
            InstallStatus.DOWNGRADE -> "Re-install / Downgrade"
            InstallStatus.SAME_VERSION -> "Re-install Application"
          }
          Text(text = btnLabel, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
      }

      // Package Specification Details Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(
              text = "Package Specifications",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            SpecRow("File Name", apk.fileName)
            SpecRow("File Size", apk.formattedSize)
            SpecRow("Target Android SDK", "API ${apk.targetSdkVersion}")
            SpecRow("Minimum Android SDK", "API ${apk.minSdkVersion}")
            SpecRow("Storage Path", apk.cachedFilePath, isMonospace = true)
          }
        }
      }

      // Permissions Audit Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp)
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Requested Permissions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
              ) {
                Text(
                  text = "${apk.permissions.size}",
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold
                )
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (apk.permissions.isEmpty()) {
              Text(
                text = "No special permissions requested.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else {
              apk.permissions.forEach { perm ->
                PermissionRow(perm)
              }
            }
          }
        }
      }

      // Choose another APK button
      item {
        OutlinedButton(
          onClick = onPickApk,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Select Another APK")
        }
      }
    }
  }
}

@Composable
private fun StatusBadge(status: InstallStatus) {
  val (bgColor, textColor, label) = when (status) {
    InstallStatus.NEW_INSTALL -> Triple(
      MaterialTheme.colorScheme.primaryContainer,
      MaterialTheme.colorScheme.onPrimaryContainer,
      "New Installation"
    )
    InstallStatus.UPDATE -> Triple(
      Color(0xFFE8F5E9),
      Color(0xFF1B5E20),
      "Update Available"
    )
    InstallStatus.DOWNGRADE -> Triple(
      Color(0xFFFFF3E0),
      Color(0xFFE65100),
      "Version Downgrade"
    )
    InstallStatus.SAME_VERSION -> Triple(
      MaterialTheme.colorScheme.secondaryContainer,
      MaterialTheme.colorScheme.onSecondaryContainer,
      "Currently Installed"
    )
  }

  Surface(
    color = bgColor,
    shape = RoundedCornerShape(8.dp)
  ) {
    Text(
      text = label,
      color = textColor,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    )
  }
}

@Composable
private fun SpecRow(label: String, value: String, isMonospace: Boolean = false) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
      fontWeight = FontWeight.Medium,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 220.dp)
    )
  }
}

@Composable
private fun PermissionRow(permission: String) {
  val shortName = permission.substringAfterLast(".")
  val isDangerous = permission.contains("CAMERA") ||
    permission.contains("LOCATION") ||
    permission.contains("RECORD_AUDIO") ||
    permission.contains("CONTACTS") ||
    permission.contains("STORAGE")

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (isDangerous) Icons.Default.Warning else Icons.Default.Security,
      contentDescription = null,
      tint = if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Column {
      Text(
        text = shortName,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = permission,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun FeatureHighlightBadge(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.padding(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.surfaceVariant),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp)
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
private fun AppExtractorTabContent(
  uiState: ApkInstallerUiState,
  onSearchChange: (String) -> Unit,
  onToggleSystemApps: () -> Unit,
  onRefresh: () -> Unit,
  onExtractApp: (InstalledAppItem) -> Unit
) {
  Column(modifier = Modifier.fillMaxSize()) {
    // Search & Filter bar
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
      OutlinedTextField(
        value = uiState.searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("Search installed packages...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (uiState.searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchChange("") }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear")
            }
          }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("search_input")
      )

      Spacer(modifier = Modifier.height(8.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        FilterChip(
          selected = uiState.showSystemApps,
          onClick = onToggleSystemApps,
          label = { Text("Show System Apps") },
          leadingIcon = if (uiState.showSystemApps) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
          } else null
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${uiState.filteredApps.size} apps",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.width(4.dp))
          IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Refresh apps list")
          }
        }
      }
    }

    // App List
    if (uiState.filteredApps.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = if (uiState.searchQuery.isNotEmpty()) "No matching applications found." else "No installed applications found.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(
          items = uiState.filteredApps,
          key = { it.packageName }
        ) { app ->
          InstalledAppCard(
            app = app,
            onExtract = { onExtractApp(app) }
          )
        }
      }
    }
  }
}

@Composable
private fun InstalledAppCard(
  app: InstalledAppItem,
  onExtract: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (app.iconBitmap != null) {
        Image(
          bitmap = app.iconBitmap.asImageBitmap(),
          contentDescription = app.appName,
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
        )
      } else {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            Icons.Default.Android,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = app.appName,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = app.packageName,
          style = MaterialTheme.typography.labelSmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = "v${app.versionName} • ${app.formattedSize}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Button(
        onClick = onExtract,
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Icon(
          Icons.Default.FileDownload,
          contentDescription = "Extract APK",
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text("Extract", fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun SystemInfoTabContent(
  uiState: ApkInstallerUiState,
  onCheckPermission: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "Device & Installer Status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(12.dp))

          SpecRow("Android OS Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
          SpecRow("Device Model", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
          SpecRow(
            label = "Unknown App Installs",
            value = if (uiState.canInstallPackages) "Permitted ✓" else "Not Allowed ✗"
          )
          Spacer(modifier = Modifier.height(12.dp))

          OutlinedButton(
            onClick = onCheckPermission,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Default.Settings, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Unknown App Sources")
          }
        }
      }
    }

    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "How APK Installation Works",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "1. Select or extract any valid Android .apk package file.\n\n" +
              "2. The engine parses the AndroidManifest binary, verifies the package signature, targets SDK compatibility, and audits all requested permissions.\n\n" +
              "3. Upon tapping Install, Android's native PackageInstaller is initiated securely via content URI FileProvider.\n\n" +
              "4. Ensure 'Allow from this source' is granted in Android system settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
          )
        }
      }
    }
  }
}
