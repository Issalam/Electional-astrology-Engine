package com.example.model

import android.graphics.Bitmap

data class InstalledAppItem(
  val appName: String,
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
  val isSystemApp: Boolean,
  val apkPath: String,
  val apkSize: Long,
  val formattedSize: String,
  val iconBitmap: Bitmap?
)
