package com.shahar.appblocker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import kotlin.math.ceil

object NotificationHelper {
    private const val CHANNEL = "local_time_limits_v2"

    fun maybeNotify(
        context: Context,
        pkg: String,
        appLabel: String,
        windowStart: Long,
        remainingSeconds: Long,
        settings: org.json.JSONObject
    ) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val manager =
            context.getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Time limit warnings",
                NotificationManager.IMPORTANCE_HIGH
            )
        )

        val marks =
            context.getSharedPreferences(
                "local_notify_marks_v2",
                Context.MODE_PRIVATE
            )

        if (remainingSeconds <= 0L) {
            val key = "$pkg@$windowStart@reached"
            if (marks.getBoolean(key, false)) return

            manager.notify(
                key.hashCode(),
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(appLabel)
                    .setContentText("Time limit reached")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .build()
            )

            marks.edit().putBoolean(key, true).apply()
            return
        }

        val thresholds = try {
            JSONArray(
                settings.optString(
                    "notification_thresholds",
                    "[15,10,5,1]"
                )
            )
        } catch (_: Exception) {
            JSONArray("[15,10,5,1]")
        }

        val remainingMinutes =
            ceil(remainingSeconds / 60.0).toInt()

        val crossed = mutableListOf<Int>()
        for (i in 0 until thresholds.length()) {
            val threshold = thresholds.optInt(i, -1)
            if (threshold >= 0 && remainingMinutes <= threshold) {
                crossed += threshold
            }
        }

        val threshold = crossed.minOrNull() ?: return
        val key = "$pkg@$windowStart@$threshold"
        if (marks.getBoolean(key, false)) return

        manager.notify(
            key.hashCode(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(appLabel)
                .setContentText(
                    "$remainingMinutes minute${
                        if (remainingMinutes == 1) "" else "s"
                    } remaining"
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )

        marks.edit().putBoolean(key, true).apply()
    }
}
