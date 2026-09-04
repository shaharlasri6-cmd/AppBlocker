package com.shahar.appblocker

import android.content.Context
import java.io.File

object LocalMigration {
    private const val PREFS = "local_v2_migration"

    fun runOnce(context: Context) {
        val marker = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean("legacy_removed", false)) return

        // Remove all legacy server/pairing state from the phone.
        for (name in listOf(
            "device",
            "policy",
            "images",
            "sync_health",
            "notify_marks",
            "notify_marks_v2",
            "usage"
        )) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }

        // Remove images previously downloaded from the web server.
        for (name in listOf(
            "general_image.png",
            "general_image.jpg",
            "time_image.png",
            "time_image.jpg"
        )) {
            try {
                File(context.filesDir, name).delete()
            } catch (_: Exception) {
            }
        }

        marker.edit().putBoolean("legacy_removed", true).commit()
    }
}
