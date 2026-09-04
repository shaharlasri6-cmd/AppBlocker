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
    private var accessibilityPkgAt = 0L
    private var lastTick = 0L
    private var blockShownFor: String? = null
    private var lastBlockLaunchAt = 0L

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
        accessibilityPkg = packageName
        accessibilityPkgAt = lastTick
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun ignored(pkg: String): Boolean =
        pkg == packageName ||
            pkg == "com.android.systemui" ||
            pkg == "com.miui.home" ||
            pkg == "com.mi.android.globallauncher"

    private fun foregroundFromUsageEvents(): String? {
        if (!AppChecks.usageAccess(this)) return null

        return try {
            val manager = getSystemService(UsageStatsManager::class.java)
            val end = System.currentTimeMillis()
            val events = manager.queryEvents(end - 10_000L, end + 250L)
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

            candidate
        } catch (_: Exception) {
            null
        }
    }

    private fun activePackage(): String? {
        val usagePkg = foregroundFromUsageEvents()

        if (usagePkg != null) {
            return if (ignored(usagePkg)) null else usagePkg
        }

        val pkg = accessibilityPkg ?: return null
        val age = SystemClock.elapsedRealtime() - accessibilityPkgAt
        if (age > 5_000L || ignored(pkg)) return null
        return pkg
    }

    private fun tick() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedSeconds =
            ((nowElapsed - lastTick) / 1000L).coerceIn(0L, 3L)
        lastTick = nowElapsed

        val pkg = activePackage()
        if (pkg == null) {
            blockShownFor = null
            return
        }

        enforce(pkg, elapsedSeconds)
    }

    private fun enforce(pkg: String, elapsedSeconds: Long = 0L) {
        if (ignored(pkg)) {
            blockShownFor = null
            return
        }

        val rule = store.rule(pkg)

        when (rule.mode) {
            "BLOCKED" -> {
                showBlock(pkg, "general")
                return
            }

            "TIME_LIMITED" -> {
                if (rule.window <= 0 || rule.allowance <= 0) {
                    blockShownFor = null
                    return
                }

                val windowStart =
                    ledger.windowStart(store.serverNow(), rule.window)

                if (elapsedSeconds > 0L) {
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

            else -> {
                blockShownFor = null
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val pkg = event.packageName?.toString() ?: return

        accessibilityPkg = pkg
        accessibilityPkgAt = SystemClock.elapsedRealtime()

        if (ignored(pkg)) {
            blockShownFor = null
            return
        }

        enforce(pkg)
    }

    private fun showBlock(pkg: String, reason: String) {
        if (ignored(pkg)) {
            blockShownFor = null
            return
        }

        val rule = store.rule(pkg)
        val valid =
            (reason == "general" && rule.mode == "BLOCKED") ||
                (reason == "time" && rule.mode == "TIME_LIMITED")

        if (!valid) {
            blockShownFor = null
            return
        }

        val marker = "$pkg:$reason"
        val now = SystemClock.elapsedRealtime()

        if (
            blockShownFor == marker &&
            now - lastBlockLaunchAt < 1500L
        ) {
            return
        }

        blockShownFor = marker
        lastBlockLaunchAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)

        handler.postDelayed({
            if (ignored(pkg)) return@postDelayed

            val latest = store.rule(pkg)
            val stillBlocked =
                (reason == "general" && latest.mode == "BLOCKED") ||
                    (
                        reason == "time" &&
                            latest.mode == "TIME_LIMITED" &&
                            latest.window > 0 &&
                            latest.allowance > 0 &&
                            ledger.seconds(
                                pkg,
                                ledger.windowStart(
                                    store.serverNow(),
                                    latest.window
                                )
                            ) >= latest.allowance * 60L
                        )

            if (!stillBlocked) {
                blockShownFor = null
                return@postDelayed
            }

            startActivity(
                Intent(this, BlockActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                    .putExtra("pkg", pkg)
                    .putExtra("reason", reason)
            )
        }, 120L)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }
}
