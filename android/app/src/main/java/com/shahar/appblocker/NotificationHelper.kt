package com.shahar.appblocker
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import kotlin.math.ceil

object NotificationHelper {
    private const val CHANNEL = "limits"
    fun maybeNotify(c: Context, pkg: String, appLabel: String, windowStart: Long, remainingSeconds: Long, settings: org.json.JSONObject) {
        val thresholds = try { JSONArray(settings.optString("notification_thresholds", "[15,10,5,1]")) } catch (_: Exception) { JSONArray("[15,10,5,1]") }
        val remainingMin = ceil(remainingSeconds.coerceAtLeast(0) / 60.0).toInt()
        val prefs = c.getSharedPreferences("notify_marks", Context.MODE_PRIVATE)
        for (i in 0 until thresholds.length()) {
            val t = thresholds.optInt(i, -1)
            if (t < 0 || remainingMin > t) continue
            val key = "$pkg@$windowStart@$t"
            if (prefs.getBoolean(key, false)) continue
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Time limit warnings", NotificationManager.IMPORTANCE_DEFAULT))
            val text = if (remainingSeconds <= 0) "Time limit reached" else "$remainingMin minute${if (remainingMin == 1) "" else "s"} remaining"
            nm.notify(key.hashCode(), NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(appLabel)
                .setContentText(text)
                .setAutoCancel(true)
                .build())
            prefs.edit().putBoolean(key, true).apply()
        }
    }
}
