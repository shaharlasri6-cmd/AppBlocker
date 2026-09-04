package com.shahar.appblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import kotlin.math.ceil

object NotificationHelper {
    private const val CHANNEL = "limits_v2"

    fun maybeNotify(
        c: Context,
        pkg: String,
        appLabel: String,
        windowStart: Long,
        remainingSeconds: Long,
        settings: org.json.JSONObject
    ) {
        if (!NotificationManagerCompat.from(c).areNotificationsEnabled()) return

        val prefs = c.getSharedPreferences("notify_marks_v2", Context.MODE_PRIVATE)
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Time limit warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Warnings before an application time limit is reached"
            }
        )

        if (remainingSeconds <= 0) {
            val reachedKey = "$pkg@$windowStart@reached"
            if (!prefs.getBoolean(reachedKey, false)) {
                nm.notify(
                    reachedKey.hashCode(),
                    NotificationCompat.Builder(c, CHANNEL)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                        .setContentTitle(appLabel)
                        .setContentText("Time limit reached")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()
                )
                prefs.edit().putBoolean(reachedKey, true).apply()
            }
            return
        }

        val thresholds = try {
            JSONArray(settings.optString("notification_thresholds", "[15,10,5,1]"))
        } catch (_: Exception) {
            JSONArray("[15,10,5,1]")
        }

        val remainingMin = ceil(remainingSeconds / 60.0).toInt()
        var thresholdToNotify: Int? = null

        for (i in 0 until thresholds.length()) {
            val t = thresholds.optInt(i, -1)
            if (t < 0 || remainingMin > t) continue
            if (thresholdToNotify == null || t < thresholdToNotify) {
                thresholdToNotify = t
            }
        }

        val t = thresholdToNotify ?: return
        val key = "$pkg@$windowStart@$t"
        if (prefs.getBoolean(key, false)) return

        nm.notify(
            key.hashCode(),
            NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(appLabel)
                .setContentText(
                    "$remainingMin minute${if (remainingMin == 1) "" else "s"} remaining"
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
        prefs.edit().putBoolean(key, true).apply()
    }
}
