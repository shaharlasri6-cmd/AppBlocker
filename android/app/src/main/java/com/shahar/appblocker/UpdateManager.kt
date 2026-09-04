package com.shahar.appblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    data class Release(val version: String, val apkUrl: String, val pageUrl: String)

    private const val PREFS = "app_updates"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LATEST_VERSION = "latest_version"
    private const val KEY_LATEST_APK = "latest_apk"
    private const val KEY_LATEST_PAGE = "latest_page"
    private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val CHANNEL = "updates"

    fun currentVersion(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "0.0.0"
    } catch (_: Exception) { "0.0.0" }

    fun configured(c: Context): Boolean {
        val owner = c.getString(R.string.github_owner)
        return owner.isNotBlank() && owner != "__GITHUB_OWNER__"
    }

    fun cachedRelease(c: Context): Release? {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val v = p.getString(KEY_LATEST_VERSION, "") ?: ""
        val apk = p.getString(KEY_LATEST_APK, "") ?: ""
        val page = p.getString(KEY_LATEST_PAGE, "") ?: ""
        if (v.isBlank() || apk.isBlank() || !isNewer(v, currentVersion(c))) return null
        return Release(v, apk, page)
    }

    fun checkLatest(c: Context): Release? {
        if (!configured(c)) return null
        val owner = c.getString(R.string.github_owner)
        val repo = c.getString(R.string.github_repo)
        val conn = (URL("https://api.github.com/repos/$owner/$repo/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AppBlocker-Android-Updater")
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("GitHub update check failed ($code)")
        val json = JSONObject(body)
        val tag = json.optString("tag_name").removePrefix("v")
        val page = json.optString("html_url")
        val assets = json.optJSONArray("assets")
        var apk = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                if (name.endsWith(".apk", true)) {
                    apk = a.optString("browser_download_url")
                    break
                }
            }
        }
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
            .putString(KEY_LATEST_VERSION, tag)
            .putString(KEY_LATEST_APK, apk)
            .putString(KEY_LATEST_PAGE, page)
            .apply()
        if (tag.isBlank() || apk.isBlank() || !isNewer(tag, currentVersion(c))) return null
        return Release(tag, apk, page)
    }

    fun maybeCheckBackground(c: Context) {
        if (!configured(c)) return
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - p.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) return
        try {
            val release = checkLatest(c) ?: return
            notifyAvailable(c, release)
        } catch (_: Exception) { }
    }

    private fun notifyAvailable(c: Context, release: Release) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Software updates", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(c, 301, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        nm.notify(301, NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Software update available")
            .setContentText("Version ${release.version} is ready to install")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build())
    }

    fun downloadAndInstall(c: Context, release: Release, status: (String) -> Unit) {
        Thread {
            try {
                status("Downloading version ${release.version}…")
                val dir = File(c.cacheDir, "updates").apply { mkdirs() }
                val apk = File(dir, "DeviceProtection-v${release.version}.apk")
                val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "AppBlocker-Android-Updater")
                }
                if (conn.responseCode !in 200..299) throw IllegalStateException("Download failed (${conn.responseCode})")
                conn.inputStream.use { input -> apk.outputStream().use { output -> input.copyTo(output) } }
                status("Download complete. Opening Android installer…")
                installApk(c, apk)
            } catch (e: Exception) {
                status("Update failed: ${e.message ?: "unknown error"}")
            }
        }.start()
    }

    private fun installApk(c: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !c.packageManager.canRequestPackageInstalls()) {
            val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            c.startActivity(settings)
            throw IllegalStateException("Allow 'Install unknown apps' for this app, then tap Install update again")
        }
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", apk)
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        c.startActivity(install)
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
