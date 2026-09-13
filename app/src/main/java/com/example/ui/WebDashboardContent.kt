package com.example.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.web.WebServerState

@Composable
fun WebDashboardContent(
  webServerState: WebServerState,
  onStartServer: () -> Unit,
  onStopServer: () -> Unit,
  onDownloadRemoteUrl: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var subTab by remember { mutableIntStateOf(0) }
  var remoteUrlInput by remember { mutableStateOf("") }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var webLoadingProgress by remember { mutableIntStateOf(0) }
  var isWebLoading by remember { mutableStateOf(false) }

  Column(modifier = modifier.fillMaxSize()) {
    // Sub tabs: In-App Web Browser vs Wi-Fi Web Server & URL Downloader
    TabRow(
      selectedTabIndex = subTab,
      containerColor = MaterialTheme.colorScheme.surface
    ) {
      Tab(
        selected = subTab == 0,
        onClick = { subTab = 0 },
        text = { Text("Web App Client") },
        icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.testTag("tab_sub_browser")
      )
      Tab(
        selected = subTab == 1,
        onClick = { subTab = 1 },
        text = { Text("Wi-Fi Sideload Server") },
        icon = { Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.testTag("tab_sub_server")
      )
      Tab(
        selected = subTab == 2,
        onClick = { subTab = 2 },
        text = { Text("Web APK Repositories") },
        icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp)) },
        modifier = Modifier.testTag("tab_sub_downloader")
      )
    }

    when (subTab) {
      // 0: Embedded Web App Browser (serves the Web App locally inside the phone)
      0 -> {
        Column(modifier = Modifier.fillMaxSize()) {
          // Web toolbar
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(
              onClick = {
                if (webViewRef?.canGoBack() == true) {
                  webViewRef?.goBack()
                }
              }
            ) {
              Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
            }

            IconButton(
              onClick = { webViewRef?.reload() }
            ) {
              Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(20.dp))
            }

            Text(
              text = if (webServerState.isRunning) "Local Web Hub: ${webServerState.loopbackUrl}" else "Web Server Offline",
              style = MaterialTheme.typography.labelSmall,
              fontFamily = FontFamily.Monospace,
              modifier = Modifier.weight(1f),
              maxLines = 1
            )

            IconButton(
              onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webServerState.serverUrl))
                context.startActivity(intent)
              }
            ) {
              Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in Chrome/Browser", modifier = Modifier.size(20.dp))
            }
          }

          if (isWebLoading) {
            LinearProgressIndicator(
              progress = { webLoadingProgress / 100f },
              modifier = Modifier.fillMaxWidth()
            )
          }

          if (webServerState.isRunning) {
            AndroidView(
              modifier = Modifier.fillMaxSize(),
              factory = { ctx ->
                WebView(ctx).apply {
                  setupCustomWebView(this) { loading, progress ->
                    isWebLoading = loading
                    webLoadingProgress = progress
                  }
                  webViewRef = this
                  loadUrl(webServerState.loopbackUrl)
                }
              },
              update = { wv ->
                webViewRef = wv
              }
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  Icons.Default.Wifi,
                  contentDescription = null,
                  modifier = Modifier.size(48.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  text = "Web Server is Currently Inactive",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Start the embedded HTTP server to load the Web App dashboard.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartServer) {
                  Icon(Icons.Default.PlayArrow, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Start Web Server")
                }
              }
            }
          }
        }
      }

      // 1: Wi-Fi Sideload Server details & management
      1 -> {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
              )
            ) {
              Column(modifier = Modifier.padding(18.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                      modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (webServerState.isRunning) Color(0xFF22C55E) else Color(0xFFEF4444))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                      text = if (webServerState.isRunning) "Server Active" else "Server Stopped",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )
                  }

                  if (webServerState.isRunning) {
                    OutlinedButton(
                      onClick = onStopServer,
                      colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                      ),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                      Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Stop")
                    }
                  } else {
                    Button(
                      onClick = onStartServer,
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                      Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(4.dp))
                      Text("Start")
                    }
                  }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                  text = "Wi-Fi & LAN Access URL",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                  color = MaterialTheme.colorScheme.surface,
                  shape = RoundedCornerShape(10.dp),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text(
                      text = webServerState.serverUrl,
                      style = MaterialTheme.typography.bodyLarge,
                      fontFamily = FontFamily.Monospace,
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                      onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Server URL", webServerState.serverUrl))
                      }
                    ) {
                      Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                    }
                  }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = "Open this link in any web browser (on your PC, Mac, iPad, or another phone connected to the same Wi-Fi network) to wirelessly upload APKs, inspect packages, or backup installed apps.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  OutlinedButton(
                    onClick = {
                      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webServerState.serverUrl))
                      context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Browser")
                  }

                  OutlinedButton(
                    onClick = {
                      val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Access APK Web Hub at: ${webServerState.serverUrl}")
                        type = "text/plain"
                      }
                      context.startActivity(Intent.createChooser(sendIntent, "Share Web URL"))
                    },
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share URL")
                  }
                }
              }
            }
          }

          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp)
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Text(
                  text = "Web Server Capabilities",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = "• Seamless Drag & Drop wireless APK sideloading\n" +
                    "• Full Android Manifest & permission verification in browser\n" +
                    "• Backup and download installed APKs directly to your PC\n" +
                    "• Local HTTP REST API: /api/apps, /api/upload, /api/status",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  lineHeight = 22.sp
                )
              }
            }
          }
        }
      }

      // 2: Web APK Repositories & Remote URL Downloader
      2 -> {
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
                  text = "Direct APK Web Downloader",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "Enter a direct web download URL pointing to any Android .apk package.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                  value = remoteUrlInput,
                  onValueChange = { remoteUrlInput = it },
                  placeholder = { Text("https://example.com/release.apk") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                  onClick = {
                    if (remoteUrlInput.isNotBlank()) {
                      onDownloadRemoteUrl(remoteUrlInput.trim())
                    }
                  },
                  modifier = Modifier.fillMaxWidth(),
                  shape = RoundedCornerShape(10.dp),
                  enabled = remoteUrlInput.isNotBlank()
                ) {
                  Icon(Icons.Default.Download, contentDescription = null)
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Download & Sideload")
                }
              }
            }
          }

          item {
            Text(
              text = "Popular Open Source Repositories",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(vertical = 4.dp)
            )
          }

          val presets = listOf(
            Triple(
              "F-Droid Open-Source Store",
              "Official Android client for F-Droid FOSS catalog",
              "https://f-droid.org/F-Droid.apk"
            ),
            Triple(
              "NewPipe Media Frontend",
              "Lightweight YouTube client without Google Play Services",
              "https://archive.newpipe.net/fdroid/repo/NewPipe_v0.27.2.apk"
            ),
            Triple(
              "VLC Media Player",
              "Universal media player by VideoLAN",
              "https://get.videolan.org/vlc-android/3.5.4/VLC-Android-3.5.4-arm64-v8a.apk"
            ),
            Triple(
              "Signal Private Messenger",
              "End-to-end encrypted messaging direct APK release",
              "https://updates.signal.org/android/Signal-Android-website-prod-universal-release-7.15.3.apk"
            )
          )

          items(presets.size) { idx ->
            val preset = presets[idx]
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  remoteUrlInput = preset.third
                  onDownloadRemoteUrl(preset.third)
                },
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = preset.first,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = preset.second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Icon(
                  Icons.Default.Download,
                  contentDescription = "Download preset",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          }
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupCustomWebView(
  webView: WebView,
  onLoadingChange: (Boolean, Int) -> Unit
) {
  webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    loadWithOverviewMode = true
    useWideViewPort = true
    cacheMode = WebSettings.LOAD_DEFAULT
    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
  }

  webView.webChromeClient = object : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
      onLoadingChange(newProgress < 100, newProgress)
    }
  }

  webView.webViewClient = object : WebViewClient() {
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
      onLoadingChange(true, 0)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
      onLoadingChange(false, 100)
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
      val url = request?.url?.toString() ?: return false
      if (url.endsWith(".apk", ignoreCase = true) || url.contains("/api/download-apk")) {
        // Direct APK download link
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        view?.context?.startActivity(intent)
        return true
      }
      return false
    }
  }
}
