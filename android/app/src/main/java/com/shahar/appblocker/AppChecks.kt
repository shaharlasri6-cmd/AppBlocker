package com.shahar.appblocker

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat

object AppChecks {
    fun accessibilityEnabled(context: Context): Boolean {
        val manager =
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        ).any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }

    @Suppress("DEPRECATION")
    fun usageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    fun notificationsEnabled(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun overlay(context: Context): Boolean = Settings.canDrawOverlays(context)
}
