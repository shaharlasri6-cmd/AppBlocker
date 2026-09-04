package com.shahar.appblocker

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

class GuardService : Service() {
    private val h = Handler(Looper.getMainLooper())
    private val task = object : Runnable {
        override fun run() { sync(); h.postDelayed(this, 30000) }
    }

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel("guard", "Protection", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        startForeground(42, NotificationCompat.Builder(this, "guard")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Device protection service active")
            .setContentText("Policies are enforced locally")
            .setOngoing(true).build())
        h.post(task)
    }

    private fun sync() {
        val api = ApiClient(this)
        if (!api.paired()) return
        Thread {
            try {
                val p = api.policy()
                PolicyStore(this).savePolicy(p)
                ImageCache.sync(this, api, p.optJSONObject("settings") ?: JSONObject())
                Inventory.sync(this, api)
                val arr = JSONArray()
                UsageLedger(this).snapshot().forEach {
                    arr.put(JSONObject().put("package_name", it.first).put("bucket_start", it.second).put("seconds", it.third))
                }
                api.post("/api/device/usage", JSONObject().put("usage", arr))
                val issues = mutableListOf<String>()
                if (!AppChecks.accessibilityEnabled(this)) issues += "Accessibility service is disabled"
                if (!AppChecks.usageAccess(this)) issues += "Usage access is missing"
                api.post("/api/device/status", JSONObject()
                    .put("protection_status", if (issues.isEmpty()) "healthy" else "degraded")
                    .put("tamper", JSONArray(issues)))
                UpdateManager.maybeCheckBackground(this)
            } catch (_: Exception) { }
        }.start()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int { sync(); return START_STICKY }
    override fun onBind(i: Intent?) = null
    override fun onDestroy() { h.removeCallbacks(task); super.onDestroy() }
}
