package com.shahar.appblocker
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

class GuardAccessibilityService : AccessibilityService() {
    private lateinit var store: PolicyStore
    private lateinit var ledger: UsageLedger
    private val handler = Handler(Looper.getMainLooper())
    private var currentPkg: String? = null
    private var lastTick = 0L
    private var blockShownFor: String? = null

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onServiceConnected() {
        store = PolicyStore(this)
        ledger = UsageLedger(this)
        lastTick = SystemClock.elapsedRealtime()
        handler.post(ticker)
    }

    private fun tick() {
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedSec = ((nowElapsed - lastTick) / 1000).coerceIn(0, 3)
        lastTick = nowElapsed
        val pkg = currentPkg ?: return
        val rule = store.rule(pkg)
        if (rule.mode == "BLOCKED") {
            showBlock(pkg, "general")
            return
        }
        if (rule.mode != "TIME_LIMITED" || rule.window <= 0 || rule.allowance <= 0) return
        val windowStart = ledger.windowStart(store.serverNow(), rule.window)
        if (elapsedSec > 0) ledger.add(pkg, windowStart, elapsedSec)
        val used = ledger.seconds(pkg, windowStart)
        val remaining = rule.allowance * 60L - used
        val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { pkg }
        NotificationHelper.maybeNotify(this, pkg, label, windowStart, remaining, store.settings())
        if (remaining <= 0) showBlock(pkg, "time")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (pkg == "com.android.systemui") {
            currentPkg = null
            blockShownFor = null
            return
        }
        currentPkg = pkg
        blockShownFor = null
        lastTick = SystemClock.elapsedRealtime()
        val rule = store.rule(pkg)
        when (rule.mode) {
            "BLOCKED" -> showBlock(pkg, "general")
            "TIME_LIMITED" -> {
                val ws = ledger.windowStart(store.serverNow(), rule.window)
                if (ledger.seconds(pkg, ws) >= rule.allowance * 60L) showBlock(pkg, "time")
            }
        }
    }

    private fun showBlock(pkg: String, reason: String) {
        val marker = "$pkg:$reason"
        if (blockShownFor == marker) return
        blockShownFor = marker
        startActivity(Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("pkg", pkg)
            .putExtra("reason", reason))
    }

    override fun onInterrupt() {}
    override fun onDestroy() { handler.removeCallbacks(ticker); super.onDestroy() }
}
