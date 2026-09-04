package com.shahar.appblocker

import android.accessibilityservice.AccessibilityService
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var store: PolicyStore
    private lateinit var ledger: UsageLedger
    private val handler = Handler(Looper.getMainLooper())

    private var accessibilityPkg: String? = null
    private var lastTick = 0L
    private var blockShownFor: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onServiceConnected() {
        store = PolicyStore(this)
        ledger = UsageLedger(this)
        lastTick = SystemClock.elapsedRealtime()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun ignored(pkg: String): Boolean =
        pkg == packageName || pkg == "com.android.systemui"

    private fun foregroundFromUsageEvents(): String? {
        if (!AppChecks.usageAccess(this)) return null
        return try {
            val manager = getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            val events = manager.queryEvents(end - 15_000L, end + 250L)
            val event = UsageEvents.Event()
            var candidate: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val foreground =
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                        (
                            Build.VERSION.SDK_INT >= 29 &&
                                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
                            )

                if (foreground && !event.packageName.isNullOrBlank()) {
                    candidate = event.packageName
                }
            }

            candidate?.takeUnless(::ignored)
        } catch (_: Exception) {
            null
        }
    }

    private fun activePackage(): String? =
        foregroundFromUsageEvents()
            ?: accessibilityPkg?.takeUnless(::ignored)

    private fun tick() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedSeconds =
            ((nowElapsed - lastTick) / 1000L).coerceIn(0L, 3L)
        lastTick = nowElapsed

        val pkg = activePackage() ?: return
        if (ignored(pkg)) return

        val rule = store.rule(pkg)

        if (rule.mode == "BLOCKED") {
            showBlock(pkg, "general")
            return
        }

        if (
            rule.mode != "TIME_LIMITED" ||
            rule.window <= 0 ||
            rule.allowance <= 0
        ) {
            blockShownFor = null
            return
        }

        val windowStart =
            ledger.windowStart(store.serverNow(), rule.window)

        if (elapsedSeconds > 0) {
            ledger.add(pkg, windowStart, elapsedSeconds)
        }

        val used = ledger.seconds(pkg, windowStart)
        val remaining = rule.allowance * 60L - used

        val label = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            pkg
        }

        NotificationHelper.maybeNotify(
            this,
            pkg,
            label,
            windowStart,
            remaining,
            store.settings()
        )

        if (remaining <= 0L) {
            showBlock(pkg, "time")
        } else if (blockShownFor == "$pkg:time") {
            blockShownFor = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        if (ignored(pkg)) {
            accessibilityPkg = null
            blockShownFor = null
            return
        }

        accessibilityPkg = pkg
        blockShownFor = null

        when (val rule = store.rule(pkg)) {
            is Rule -> {
                when (rule.mode) {
                    "BLOCKED" -> showBlock(pkg, "general")
                    "TIME_LIMITED" -> {
                        if (rule.window <= 0 || rule.allowance <= 0) return
                        val start =
                            ledger.windowStart(store.serverNow(), rule.window)
                        val remaining =
                            rule.allowance * 60L - ledger.seconds(pkg, start)
                        if (remaining <= 0L) {
                            showBlock(pkg, "time")
                        }
                    }
                }
            }
        }
    }

    private fun showBlock(pkg: String, reason: String) {
        if (ignored(pkg)) {
            blockShownFor = null
            return
        }

        val marker = "$pkg:$reason"
        if (blockShownFor == marker) return
        blockShownFor = marker

        startActivity(
            Intent(this, BlockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                .putExtra("pkg", pkg)
                .putExtra("reason", reason)
        )
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
