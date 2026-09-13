package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.model.ApkDetails
import com.example.model.InstallStatus
import com.example.model.InstalledAppItem
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.DecimalFormat

object ApkUtils {

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val groupIndex = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, groupIndex.toDouble())
    val df = DecimalFormat("#,##0.#")
    return "${df.format(value)} ${units[groupIndex]}"
  }

  fun drawableToBitmap(drawable: Drawable?): Bitmap? {
    if (drawable == null) return null
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
      return drawable.bitmap
    }
    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 128
    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 128
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
  }

  fun getFileNameAndSizeFromUri(context: Context, uri: Uri): Pair<String, Long> {
    var name = "app.apk"
    var size = 0L
    try {
      context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
          val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (nameIndex != -1) {
            name = cursor.getString(nameIndex) ?: name
          }
          val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
          if (sizeIndex != -1) {
            size = cursor.getLong(sizeIndex)
          }
        }
      }
    } catch (_: Exception) {
      // Fallback
      val lastSegment = uri.lastPathSegment
      if (!lastSegment.isNullOrBlank()) {
        name = lastSegment
      }
    }
    return Pair(name, size)
  }

  fun parseApkFromUri(context: Context, uri: Uri): Result<ApkDetails> {
    return try {
      val (displayName, estimatedSize) = getFileNameAndSizeFromUri(context, uri)
      val tempDir = File(context.cacheDir, "apks").apply { mkdirs() }
      val cleanFileName = if (displayName.endsWith(".apk", ignoreCase = true)) {
        displayName
      } else {
        "$displayName.apk"
      }
      val targetFile = File(tempDir, "${System.currentTimeMillis()}_$cleanFileName")

      context.contentResolver.openInputStream(uri)?.use { input ->
        FileOutputStream(targetFile).use { output ->
          input.copyTo(output)
        }
      } ?: return Result.failure(Exception("Unable to read input stream from selected APK"))

      parseApkFromFile(context, targetFile, cleanFileName, uri)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun parseApkFromFile(
    context: Context,
    apkFile: File,
    originalFileName: String = apkFile.name,
    originalUri: Uri? = null
  ): Result<ApkDetails> {
    return try {
      val pm = context.packageManager
      val flags = PackageManager.GET_PERMISSIONS
      val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)

      if (packageInfo == null) {
        return Result.failure(Exception("Failed to parse APK archive. File may be corrupted or invalid."))
      }

      val appInfo: ApplicationInfo = packageInfo.applicationInfo ?: ApplicationInfo()
      appInfo.sourceDir = apkFile.absolutePath
      appInfo.publicSourceDir = apkFile.absolutePath

      val appLabel = try {
        appInfo.loadLabel(pm).toString()
      } catch (_: Exception) {
        packageInfo.packageName
      }

      val iconDrawable = try {
        appInfo.loadIcon(pm)
      } catch (_: Exception) {
        null
      }
      val iconBitmap = drawableToBitmap(iconDrawable)

      val packageName = packageInfo.packageName ?: "unknown.package"
      val versionName = packageInfo.versionName ?: "1.0"
      val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
      }

      val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        appInfo.minSdkVersion
      } else {
        21
      }
      val targetSdk = appInfo.targetSdkVersion

      val permissionsList = packageInfo.requestedPermissions?.toList() ?: emptyList()

      // Check if installed on device
      var isInstalled = false
      var installedVersionName: String? = null
      var installedVersionCode: Long? = null
      var status = InstallStatus.NEW_INSTALL

      try {
        val installedPkg = pm.getPackageInfo(packageName, 0)
        isInstalled = true
        installedVersionName = installedPkg.versionName
        installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          installedPkg.longVersionCode
        } else {
          @Suppress("DEPRECATION")
          installedPkg.versionCode.toLong()
        }

        status = when {
          versionCode > installedVersionCode -> InstallStatus.UPDATE
          versionCode < installedVersionCode -> InstallStatus.DOWNGRADE
          else -> InstallStatus.SAME_VERSION
        }
      } catch (_: PackageManager.NameNotFoundException) {
        isInstalled = false
        status = InstallStatus.NEW_INSTALL
      }

      val fileSize = apkFile.length()

      Result.success(
        ApkDetails(
          fileName = originalFileName,
          fileSize = fileSize,
          formattedSize = formatBytes(fileSize),
          packageName = packageName,
          appName = if (appLabel.isNotBlank()) appLabel else packageName,
          versionName = versionName,
          versionCode = versionCode,
          minSdkVersion = minSdk,
          targetSdkVersion = targetSdk,
          iconBitmap = iconBitmap,
          permissions = permissionsList,
          isAlreadyInstalled = isInstalled,
          installedVersionName = installedVersionName,
          installedVersionCode = installedVersionCode,
          installStatus = status,
          sourceUri = originalUri,
          cachedFilePath = apkFile.absolutePath
        )
      )
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun canRequestPackageInstalls(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  fun createInstallIntent(context: Context, apkFile: File): Intent {
    val authority = "${context.packageName}.fileprovider"
    val contentUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)

    return Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(contentUri, "application/vnd.android.package-archive")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  }

  fun openUnknownAppSourcesSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } else {
      val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    }
  }

  fun getInstalledAppsList(context: Context): List<InstalledAppItem> {
    val pm = context.packageManager
    val packages = pm.getInstalledPackages(0)
    val result = mutableListOf<InstalledAppItem>()

    for (pkg in packages) {
      val appInfo = pkg.applicationInfo ?: continue
      val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
      val appName = try {
        appInfo.loadLabel(pm).toString()
      } catch (_: Exception) {
        pkg.packageName
      }

      val apkPath = appInfo.sourceDir ?: ""
      val apkFile = if (apkPath.isNotEmpty()) File(apkPath) else null
      val size = apkFile?.length() ?: 0L

      val iconDrawable = try {
        appInfo.loadIcon(pm)
      } catch (_: Exception) {
        null
      }
      val iconBitmap = drawableToBitmap(iconDrawable)

      val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkg.longVersionCode
      } else {
        @Suppress("DEPRECATION")
        pkg.versionCode.toLong()
      }

      result.add(
        InstalledAppItem(
          appName = if (appName.isNotBlank()) appName else pkg.packageName,
          packageName = pkg.packageName,
          versionName = pkg.versionName ?: "1.0",
          versionCode = vCode,
          isSystemApp = isSystem,
          apkPath = apkPath,
          apkSize = size,
          formattedSize = formatBytes(size),
          iconBitmap = iconBitmap
        )
      )
    }

    // Sort: Non-system apps first alphabetically, then system apps
    return result.sortedWith(
      compareBy<InstalledAppItem> { it.isSystemApp }
        .thenBy { it.appName.lowercase() }
    )
  }

  fun extractApkToFile(context: Context, sourceApkPath: String, targetFileName: String): Result<File> {
    return try {
      val source = File(sourceApkPath)
      if (!source.exists() || !source.canRead()) {
        return Result.failure(Exception("Cannot read source APK: $sourceApkPath"))
      }

      val extractDir = File(context.cacheDir, "extracted_apks").apply { mkdirs() }
      val cleanName = if (targetFileName.endsWith(".apk")) targetFileName else "$targetFileName.apk"
      val target = File(extractDir, cleanName)

      source.inputStream().use { input ->
        FileOutputStream(target).use { output ->
          input.copyTo(output)
        }
      }
      Result.success(target)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun getSelfApk(context: Context): Result<File> {
    return try {
      val sourceDir = context.applicationInfo.sourceDir
      if (sourceDir.isNullOrBlank()) {
        return Result.failure(Exception("App source directory not found"))
      }
      val selfFile = File(sourceDir)
      if (!selfFile.exists()) {
        return Result.failure(Exception("App APK does not exist at $sourceDir"))
      }
      Result.success(selfFile)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }
}
