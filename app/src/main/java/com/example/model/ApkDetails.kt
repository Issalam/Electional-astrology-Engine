package com.example.model

import android.graphics.Bitmap
import android.net.Uri

enum class InstallStatus {
  NEW_INSTALL,
  UPDATE,
  DOWNGRADE,
  SAME_VERSION
}

data class ApkDetails(
  val fileName: String,
  val fileSize: Long,
  val formattedSize: String,
  val packageName: String,
  val appName: String,
  val versionName: String,
  val versionCode: Long,
  val minSdkVersion: Int,
  val targetSdkVersion: Int,
  val iconBitmap: Bitmap?,
  val permissions: List<String>,
  val isAlreadyInstalled: Boolean,
  val installedVersionName: String? = null,
  val installedVersionCode: Long? = null,
  val installStatus: InstallStatus = InstallStatus.NEW_INSTALL,
  val sourceUri: Uri? = null,
  val cachedFilePath: String
)
