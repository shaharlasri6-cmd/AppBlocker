package com.shahar.appblocker

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private val api by lazy { ApiClient(this) }
    private val bg = Color.rgb(9, 14, 20)
    private val panel = Color.rgb(18, 26, 36)
    private val line = Color.rgb(40, 56, 74)
    private val textColor = Color.rgb(238, 244, 251)
    private val muted = Color.rgb(145, 162, 181)
    private val accent = Color.rgb(75, 143, 247)
    private val good = Color.rgb(82, 209, 139)
    private val bad = Color.rgb(255, 114, 114)

    override fun onCreate(b: Bundle?) { super.onCreate(b); window.statusBarColor = bg; window.navigationBarColor = bg; if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 700); draw() }
    override fun onResume() { super.onResume(); if (api.paired()) { draw(); startForegroundService(Intent(this, GuardService::class.java)) } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, radius: Int = 18, stroke: Int? = line) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun txt(s: String, size: Float = 15f, color: Int = textColor, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD); setLineSpacing(0f, 1.12f)
    }
    private fun button(label: String, primary: Boolean = false, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(textColor); background = shape(if (primary) accent else Color.rgb(26, 38, 52), 12); setPadding(dp(14), dp(10), dp(14), dp(10)); setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)); background = shape(panel)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }
    private fun statusCard(title: String, ok: Boolean, detail: String, actionLabel: String? = null, action: (() -> Unit)? = null): View {
        val c = card(); val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(txt(title, 16f, textColor, true)); left.addView(txt(detail, 12f, muted))
        row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(txt(if (ok) "ACTIVE" else "SETUP", 11f, if (ok) good else bad, true))
        c.addView(row)
        if (!ok && actionLabel != null && action != null) {
            val b = button(actionLabel, true, action); c.addView(b, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        }
        return c
    }

    private fun draw() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(24), dp(18), dp(26)); setBackgroundColor(bg) }
        val scroll = ScrollView(this).apply { setBackgroundColor(bg); addView(root) }; setContentView(scroll)
        val title = txt("Device Protection Configuration and Application Usage Management Service", 24f, textColor, true); root.addView(title)
        root.addView(txt(if (api.paired()) "Protection status" else "Connect this phone", 14f, muted), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(22) })
        if (!api.paired()) pairUi() else statusUi()
    }

    private fun pairUi() {
        val c = card(); c.addView(txt("Initial pairing", 18f, textColor, true)); c.addView(txt("Blocking rules are managed only from the web dashboard. Enter your management server address to pair this phone.", 13f, muted))
        val server = EditText(this).apply { hint = "http://192.168.1.50:8787"; setHintTextColor(Color.rgb(100,120,140)); setTextColor(textColor); background = shape(Color.rgb(12,19,27),12); setPadding(dp(12),dp(10),dp(12),dp(10)) }
        c.addView(server, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) })
        val out = txt("", 14f, muted); val b = button("Create pairing code", true) {
            Thread { try { val r=api.startPair(server.text.toString()); val code=r.getString("pairing_code"); runOnUiThread { out.text="Pairing code: $code\nApprove it in the web dashboard." }; poll(code) } catch(e:Exception) { runOnUiThread { out.text=e.message ?: "Could not connect" } } }.start()
        }
        c.addView(b, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) }); c.addView(out, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) }); root.addView(c)
    }

    private fun poll(code:String) {
        Thread {
            repeat(120) {
                try {
                    if (api.pairStatus(code).optBoolean("approved")) {
                        runOnUiThread {
                            Toast.makeText(this, "Pairing approved. Syncing applications…", Toast.LENGTH_SHORT).show()
                            draw()
                            try {
                                startForegroundService(Intent(this, GuardService::class.java))
                            } catch (e: Exception) {
                                Toast.makeText(this, "Connected, but sync could not start: ${e.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                            }
                        }
                        return@Thread
                    }
                } catch (_: Exception) { }
                Thread.sleep(2000)
            }
        }.start()
    }

    private fun updateCard(): View {
        val c = card()
        c.addView(txt("Software updates", 17f, textColor, true))
        val current = UpdateManager.currentVersion(this)
        val state = txt("Current version $current", 12f, muted)
        c.addView(state)
        if (!UpdateManager.configured(this)) {
            c.addView(txt("GitHub updates are not configured yet. Run setup-github-updates.sh once on the server computer.", 11f, muted), LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
            return c
        }

        fun showRelease(release: UpdateManager.Release) {
            state.text = "Update available: v${release.version}"
            state.setTextColor(good)
            val install = button("Download and install update", true) {
                UpdateManager.downloadAndInstall(this, release) { message -> runOnUiThread { state.text = message } }
            }
            c.addView(install, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        }

        val cached = UpdateManager.cachedRelease(this)
        if (cached != null) {
            showRelease(cached)
        } else {
            val check = button("Check GitHub for updates", false) {
                state.text = "Checking GitHub…"
                Thread {
                    try {
                        val release = UpdateManager.checkLatest(this)
                        runOnUiThread {
                            if (release == null) {
                                state.text = "Current version $current — up to date"
                                state.setTextColor(muted)
                            } else {
                                showRelease(release)
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread { state.text = "Update check failed: ${e.message ?: "unknown error"}" }
                    }
                }.start()
            }
            c.addView(check, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(10) })
        }
        return c
    }

    private fun statusUi() {
        val hero = card(); hero.addView(txt("${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}", 20f, textColor, true)); hero.addView(txt("Paired with ${api.base()}\nDevice ID ${api.deviceId().take(8)}…", 12f, muted));
        hero.addView(button("Sync now", false) { startForegroundService(Intent(this, GuardService::class.java)); Toast.makeText(this,"Sync requested",Toast.LENGTH_SHORT).show() }, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(12) }); root.addView(hero)

        root.addView(updateCard())

        root.addView(txt("Protection", 18f, textColor, true), LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(10) })
        root.addView(statusCard("Accessibility", AppChecks.accessibilityEnabled(this), "Detects foreground applications and enforces blocks.", "Open Accessibility") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })
        root.addView(statusCard("Usage access", AppChecks.usageAccess(this), "Supports application usage and time-limit tracking.", "Open Usage Access") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) })
        root.addView(statusCard("Notifications", AppChecks.notificationsEnabled(this), "Required for time-limit warning notifications.", "Open Notifications") { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) })

        val instructions = card(); instructions.addView(txt("Phone reliability", 17f, textColor, true)); instructions.addView(txt("These shortcuts help keep the management service alive in the background on your phone.", 12f, muted))
        for (s in OemCompat.provider().steps(this)) {
            val b=button(s.title,false){ try{s.intent?.let{startActivity(it)}}catch(_:Exception){} }; instructions.addView(b,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(9)}); instructions.addView(txt(s.detail,11f,muted))
        }
        root.addView(instructions)

        val note=card(); note.addView(txt("Management stays on the web",15f,textColor,true)); note.addView(txt("Application blocks, time limits, images, notifications and device names are changed only from the web dashboard.",12f,muted)); root.addView(note)
    }
}
