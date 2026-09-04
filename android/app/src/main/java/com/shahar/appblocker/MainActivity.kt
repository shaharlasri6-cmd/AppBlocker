package com.shahar.appblocker

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var store: PolicyStore
    private lateinit var passwords: PasswordManager

    private val bg = Color.rgb(9, 14, 20)
    private val panel = Color.rgb(18, 26, 36)
    private val line = Color.rgb(40, 56, 74)
    private val textColor = Color.rgb(238, 244, 251)
    private val muted = Color.rgb(145, 162, 181)
    private val accent = Color.rgb(75, 143, 247)
    private val good = Color.rgb(82, 209, 139)
    private val bad = Color.rgb(255, 114, 114)

    private var pendingImageKind: String? = null
    private var appSearch = ""
    private var showSystemApps = false
    private var applications: List<AppEntry> = emptyList()
    private var appsContainer: LinearLayout? = null

    data class AppEntry(
        val packageName: String,
        val label: String,
        val system: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        LocalMigration.runOnce(this)
        store = PolicyStore(this)
        passwords = PasswordManager(this)

        if (!passwords.hasPassword()) {
            showFirstRunPassword()
        } else {
            maybeAskNotificationPermission()
            draw()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::passwords.isInitialized && passwords.hasPassword()) {
            draw()
            Thread {
                try {
                    UpdateManager.maybeCheckBackground(this)
                } catch (_: Exception) {
                }
            }.start()
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun shape(
        color: Int,
        radius: Int = 18,
        stroke: Int? = line
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun txt(
        value: String,
        size: Float = 15f,
        color: Int = textColor,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(0f, 1.12f)
    }

    private fun button(
        label: String,
        primary: Boolean = false,
        action: () -> Unit
    ) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(textColor)
        background =
            shape(
                if (primary) accent
                else Color.rgb(26, 38, 52),
                12
            )
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setOnClickListener { action() }
    }

    private fun card(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = shape(panel)
            layoutParams =
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(12)
                }
        }

    private fun sectionTitle(title: String, subtitle: String? = null) {
        root.addView(
            txt(title, 19f, textColor, true),
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
        )
        if (subtitle != null) {
            root.addView(
                txt(subtitle, 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply {
                    bottomMargin = dp(10)
                }
            )
        }
    }

    private fun showFirstRunPassword() {
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(38), dp(28), dp(28))
            setBackgroundColor(bg)
        }

        wrap.addView(txt("AppBlocker", 30f, textColor, true))
        wrap.addView(
            txt(
                "Local control setup",
                15f,
                muted
            ),
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(28)
            }
        )

        val c = card()
        c.addView(txt("Create management password", 20f, textColor, true))
        c.addView(
            txt(
                "This password is required every time a blocking rule, image, notification setting or security setting is changed.",
                13f,
                muted
            )
        )

        val first = passwordInput("Password")
        val second = passwordInput("Confirm password")
        val status = txt("", 12f, bad)

        c.addView(
            first,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(16)
            }
        )
        c.addView(
            second,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            }
        )
        c.addView(
            button("Create password", true) {
                val a = first.text.toString()
                val b = second.text.toString()

                when {
                    a.length < 6 ->
                        status.text =
                            "Use at least 6 characters."
                    a != b ->
                        status.text =
                            "Passwords do not match."
                    else -> {
                        try {
                            passwords.setInitial(a)
                            Toast.makeText(
                                this,
                                "Password created",
                                Toast.LENGTH_SHORT
                            ).show()
                            maybeAskNotificationPermission()
                            draw()
                        } catch (e: Exception) {
                            status.text =
                                e.message ?: "Could not save password"
                        }
                    }
                }
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            }
        )
        c.addView(status)
        wrap.addView(c)

        setContentView(wrap)
    }

    private fun passwordInput(hintText: String) =
        EditText(this).apply {
            hint = hintText
            setHintTextColor(Color.rgb(100, 120, 140))
            setTextColor(textColor)
            inputType =
                InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_VARIATION_PASSWORD
            background =
                shape(Color.rgb(12, 19, 27), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

    private fun requirePassword(
        title: String,
        onSuccess: () -> Unit
    ) {
        val input = passwordInput("Management password")

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Enter the management password to continue.")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    if (passwords.verify(input.text.toString())) {
                        dialog.dismiss()
                        onSuccess()
                    } else {
                        input.error = "Incorrect password"
                    }
                }
        }

        dialog.show()
    }

    private fun draw() {
        if (!passwords.hasPassword()) {
            showFirstRunPassword()
            return
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(26))
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            addView(root)
        }
        setContentView(scroll)

        root.addView(txt("AppBlocker", 30f, textColor, true))
        root.addView(
            txt(
                "Everything is managed locally on this phone.",
                14f,
                muted
            ),
            LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(18)
            }
        )

        summaryCard()
        protectionSection()
        applicationsSection()
        imagesSection()
        notificationsSection()
        securitySection()
        updatesSection()
    }

    private fun summaryCard() {
        val c = card()
        c.addView(txt("Local protection", 19f, textColor, true))

        val status =
            if (
                AppChecks.accessibilityEnabled(this) &&
                AppChecks.usageAccess(this)
            ) {
                "Protection ready"
            } else {
                "Setup required"
            }

        c.addView(
            txt(
                status,
                13f,
                if (status == "Protection ready") good else bad,
                true
            )
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val blocked = statBox(
            "Blocked",
            store.ruleCount("BLOCKED").toString()
        )
        val timed = statBox(
            "Time limited",
            store.ruleCount("TIME_LIMITED").toString()
        )

        row.addView(
            blocked,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                rightMargin = dp(6)
            }
        )
        row.addView(
            timed,
            LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(6)
            }
        )

        c.addView(
            row,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(14)
            }
        )

        root.addView(c)
    }

    private fun statBox(label: String, value: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = shape(Color.rgb(12, 19, 27), 12)
            addView(txt(value, 24f, textColor, true))
            addView(txt(label, 11f, muted))
        }

    private fun protectionSection() {
        sectionTitle(
            "Protection",
            "Android permissions needed for local blocking and time limits."
        )

        root.addView(
            statusCard(
                "Accessibility",
                AppChecks.accessibilityEnabled(this),
                "Detects the foreground application and enforces local rules.",
                "Open Accessibility"
            ) {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        )

        root.addView(
            statusCard(
                "Usage access",
                AppChecks.usageAccess(this),
                "Improves foreground detection and time-limit accuracy.",
                "Open Usage Access"
            ) {
                startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                )
            }
        )

        root.addView(
            statusCard(
                "Notifications",
                AppChecks.notificationsEnabled(this),
                "Required for time-limit warnings.",
                "Open Notifications"
            ) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            packageName
                        )
                )
            }
        )

        val oem = card()
        oem.addView(txt("Phone reliability", 16f, textColor, true))
        oem.addView(
            txt(
                "Use these Xiaomi/Android shortcuts if the protection service is being restricted by battery management.",
                12f,
                muted
            )
        )
        for (step in OemCompat.provider().steps(this)) {
            oem.addView(
                button(step.title, false) {
                    try {
                        step.intent?.let { startActivity(it) }
                    } catch (_: Exception) {
                    }
                },
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                }
            )
            oem.addView(txt(step.detail, 11f, muted))
        }
        root.addView(oem)
    }

    private fun statusCard(
        title: String,
        ok: Boolean,
        detail: String,
        actionLabel: String,
        action: () -> Unit
    ): View {
        val c = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        left.addView(txt(title, 16f, textColor, true))
        left.addView(txt(detail, 12f, muted))
        row.addView(
            left,
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        row.addView(
            txt(
                if (ok) "ACTIVE" else "SETUP",
                11f,
                if (ok) good else bad,
                true
            )
        )
        c.addView(row)

        if (!ok) {
            c.addView(
                button(actionLabel, true, action),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(12)
                }
            )
        }
        return c
    }

    private fun applicationsSection() {
        sectionTitle(
            "Applications",
            "Choose which apps are unrestricted, always blocked or time limited."
        )

        val c = card()
        val search = EditText(this).apply {
            hint = "Search applications"
            setHintTextColor(Color.rgb(100, 120, 140))
            setTextColor(textColor)
            setSingleLine(true)
            background = shape(Color.rgb(12, 19, 27), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setText(appSearch)
        }
        c.addView(search)

        val systemSwitch = Switch(this).apply {
            text = "Show system apps"
            setTextColor(textColor)
            isChecked = showSystemApps
        }
        c.addView(
            systemSwitch,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            }
        )

        val loading = txt("Loading applications…", 12f, muted)
        c.addView(
            loading,
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(12)
            }
        )

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        appsContainer = list
        c.addView(list)
        root.addView(c)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                appSearch = s?.toString() ?: ""
                renderApplications()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        systemSwitch.setOnCheckedChangeListener { _, checked ->
            showSystemApps = checked
            renderApplications()
        }

        Thread {
            val loaded = loadApplications()
            runOnUiThread {
                applications = loaded
                loading.visibility = View.GONE
                renderApplications()
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun loadApplications(): List<AppEntry> {
        val pm = packageManager
        return pm.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != packageName }
            .map {
                AppEntry(
                    it.packageName,
                    pm.getApplicationLabel(it).toString(),
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedWith(
                compareBy<AppEntry> { it.system }
                    .thenBy { it.label.lowercase() }
            )
            .toList()
    }

    private fun renderApplications() {
        val container = appsContainer ?: return
        container.removeAllViews()

        val query = appSearch.trim().lowercase()

        val filtered = applications.filter {
            (showSystemApps || !it.system) &&
                (
                    query.isBlank() ||
                        it.label.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
                    )
        }

        if (filtered.isEmpty()) {
            container.addView(
                txt("No matching applications.", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(12)
                }
            )
            return
        }

        for (app in filtered.take(250)) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }

            val icon = ImageView(this).apply {
                try {
                    setImageDrawable(
                        packageManager.getApplicationIcon(
                            app.packageName
                        )
                    )
                } catch (_: Exception) {
                }
            }
            row.addView(
                icon,
                LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                    rightMargin = dp(12)
                }
            )

            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            info.addView(txt(app.label, 14f, textColor, true))
            info.addView(txt(app.packageName, 10f, muted))

            val rule = store.rule(app.packageName)
            val state =
                when (rule.mode) {
                    "BLOCKED" -> "BLOCKED"
                    "TIME_LIMITED" ->
                        "${rule.allowance} min / ${rule.window} min"
                    else -> "UNRESTRICTED"
                }

            info.addView(
                txt(
                    state,
                    10f,
                    when (rule.mode) {
                        "BLOCKED" -> bad
                        "TIME_LIMITED" -> Color.rgb(255, 192, 92)
                        else -> good
                    },
                    true
                )
            )

            row.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f)
            )

            row.addView(
                button("Manage") {
                    showRuleDialog(app)
                }
            )

            container.addView(row)
            container.addView(
                View(this).apply {
                    setBackgroundColor(line)
                },
                LinearLayout.LayoutParams(-1, dp(1))
            )
        }

        if (filtered.size > 250) {
            container.addView(
                txt(
                    "Showing first 250 results. Use search to narrow the list.",
                    11f,
                    muted
                )
            )
        }
    }

    private fun showRuleDialog(app: AppEntry) {
        val current = store.rule(app.packageName)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), 0)
        }

        val modes =
            arrayOf("Unrestricted", "Always blocked", "Time limited")

        val spinner = Spinner(this).apply {
            adapter =
                ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    modes
                )
            setSelection(
                when (current.mode) {
                    "BLOCKED" -> 1
                    "TIME_LIMITED" -> 2
                    else -> 0
                }
            )
        }

        val allowance = EditText(this).apply {
            hint = "Allowed minutes"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(
                if (current.allowance > 0)
                    current.allowance.toString()
                else
                    ""
            )
        }

        val window = EditText(this).apply {
            hint = "Window minutes"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(
                if (current.window > 0)
                    current.window.toString()
                else
                    ""
            )
        }

        box.addView(spinner)
        box.addView(allowance)
        box.addView(window)

        fun updateFields() {
            val timed = spinner.selectedItemPosition == 2
            allowance.visibility =
                if (timed) View.VISIBLE else View.GONE
            window.visibility =
                if (timed) View.VISIBLE else View.GONE
        }

        spinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    updateFields()
                }

                override fun onNothingSelected(
                    parent: android.widget.AdapterView<*>?
                ) = Unit
            }

        updateFields()

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(app.label)
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val newRule =
                        when (spinner.selectedItemPosition) {
                            1 ->
                                Rule("BLOCKED")
                            2 -> {
                                val a =
                                    allowance.text.toString()
                                        .toIntOrNull() ?: 0
                                val w =
                                    window.text.toString()
                                        .toIntOrNull() ?: 0

                                if (a <= 0 || w <= 0 || a > w) {
                                    Toast.makeText(
                                        this,
                                        "Use positive values and keep allowed minutes ≤ window minutes.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@setOnClickListener
                                }
                                Rule("TIME_LIMITED", a, w)
                            }
                            else ->
                                Rule("UNRESTRICTED")
                        }

                    dialog.dismiss()
                    requirePassword("Confirm rule change") {
                        store.setRule(app.packageName, newRule)
                        renderApplications()
                        summaryCardRefresh()
                        Toast.makeText(
                            this,
                            "Rule updated",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        dialog.show()
    }

    private fun summaryCardRefresh() {
        draw()
    }

    private fun imagesSection() {
        sectionTitle(
            "Blocking images",
            "Choose local images shown for a normal block and for a time-limit block."
        )

        root.addView(imageCard("general", "General block image", 1101))
        root.addView(imageCard("time", "Time-limit image", 1102))
    }

    private fun imageCard(
        kind: String,
        title: String,
        requestCode: Int
    ): View {
        val c = card()
        c.addView(txt(title, 16f, textColor, true))

        val path = store.imagePath(kind)
        if (path.isNotBlank() && File(path).exists()) {
            c.addView(
                ImageView(this).apply {
                    adjustViewBounds = true
                    maxHeight = dp(220)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setImageURI(Uri.fromFile(File(path)))
                },
                LinearLayout.LayoutParams(-1, dp(180)).apply {
                    topMargin = dp(10)
                }
            )
        } else {
            c.addView(
                txt("No image selected", 12f, muted),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                }
            )
        }

        c.addView(
            button("Choose image", true) {
                requirePassword("Change blocking image") {
                    pendingImageKind = kind
                    val intent =
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                    startActivityForResult(intent, requestCode)
                }
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            }
        )

        if (path.isNotBlank()) {
            c.addView(
                button("Remove image") {
                    requirePassword("Remove blocking image") {
                        try {
                            File(path).delete()
                        } catch (_: Exception) {
                        }
                        store.setImagePath(kind, "")
                        draw()
                    }
                },
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                }
            )
        }

        return c
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK) {
            pendingImageKind = null
            return
        }

        val uri = data?.data ?: return
        val kind =
            pendingImageKind
                ?: if (requestCode == 1102) "time" else "general"
        pendingImageKind = null

        try {
            val dir = File(filesDir, "blocking_images").apply {
                mkdirs()
            }
            val output = File(dir, "$kind-image")
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                FileOutputStream(output).use { out ->
                    input.copyTo(out)
                }
            }
            store.setImagePath(kind, output.absolutePath)
            Toast.makeText(
                this,
                "Image saved locally",
                Toast.LENGTH_SHORT
            ).show()
            draw()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Could not save image: ${e.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun notificationsSection() {
        sectionTitle(
            "Time-limit notifications",
            "Choose how many minutes before the limit a warning should appear."
        )

        val c = card()
        val input = EditText(this).apply {
            setText(store.thresholds().joinToString(", "))
            hint = "15, 10, 5, 1"
            setTextColor(textColor)
            setHintTextColor(Color.rgb(100, 120, 140))
            background = shape(Color.rgb(12, 19, 27), 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        c.addView(input)
        c.addView(
            button("Save warning times", true) {
                val values =
                    input.text.toString()
                        .split(",")
                        .mapNotNull {
                            it.trim().toIntOrNull()
                        }
                        .filter { it >= 0 }

                if (values.isEmpty()) {
                    Toast.makeText(
                        this,
                        "Enter at least one number.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@button
                }

                requirePassword("Change notification settings") {
                    store.setThresholds(values)
                    Toast.makeText(
                        this,
                        "Warning times updated",
                        Toast.LENGTH_SHORT
                    ).show()
                    draw()
                }
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            }
        )

        root.addView(c)
    }

    private fun securitySection() {
        sectionTitle(
            "Security",
            "Every local configuration change requires the management password."
        )

        val c = card()
        c.addView(txt("Management password", 16f, textColor, true))
        c.addView(
            button("Change password") {
                showChangePasswordDialog()
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            }
        )

        c.addView(
            button("Reset all application rules") {
                requirePassword("Reset all application rules") {
                    AlertDialog.Builder(this)
                        .setTitle("Reset all rules?")
                        .setMessage(
                            "All applications will become unrestricted. Images and the password will stay unchanged."
                        )
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Reset") { _, _ ->
                            store.clearAllRules()
                            draw()
                        }
                        .show()
                }
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(8)
            }
        )

        root.addView(c)
    }

    private fun showChangePasswordDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(6), dp(22), 0)
        }

        val old = passwordInput("Current password")
        val fresh = passwordInput("New password")
        val confirm = passwordInput("Confirm new password")

        box.addView(old)
        box.addView(fresh)
        box.addView(confirm)

        val dialog =
            AlertDialog.Builder(this)
                .setTitle("Change management password")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Change", null)
                .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val a = old.text.toString()
                    val b = fresh.text.toString()
                    val c = confirm.text.toString()

                    when {
                        !passwords.verify(a) ->
                            old.error = "Incorrect password"
                        b.length < 6 ->
                            fresh.error =
                                "Use at least 6 characters"
                        b != c ->
                            confirm.error =
                                "Passwords do not match"
                        else -> {
                            passwords.change(a, b)
                            dialog.dismiss()
                            Toast.makeText(
                                this,
                                "Password changed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
        }

        dialog.show()
    }

    private fun updatesSection() {
        sectionTitle(
            "Software updates",
            "Updates continue to come directly from GitHub Releases."
        )

        val c = card()
        val current = UpdateManager.currentVersion(this)
        val state = txt("Current version $current", 12f, muted)
        c.addView(state)

        if (!UpdateManager.configured(this)) {
            c.addView(
                txt(
                    "GitHub updates are not configured in this build.",
                    11f,
                    bad
                ),
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(8)
                }
            )
            root.addView(c)
            return
        }

        fun showRelease(release: UpdateManager.Release) {
            state.text = "Update available: v${release.version}"
            state.setTextColor(good)

            c.addView(
                button("Download and install update", true) {
                    requirePassword("Install software update") {
                        UpdateManager.downloadAndInstall(
                            this,
                            release
                        ) { message ->
                            runOnUiThread {
                                state.text = message
                            }
                        }
                    }
                },
                LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(10)
                }
            )
        }

        val cached = UpdateManager.cachedRelease(this)
        if (cached != null) {
            showRelease(cached)
        }

        c.addView(
            button("Check GitHub for updates") {
                state.text = "Checking GitHub…"
                Thread {
                    try {
                        val release =
                            UpdateManager.checkLatest(this)
                        runOnUiThread {
                            if (release == null) {
                                state.text =
                                    "Current version $current — up to date"
                                state.setTextColor(muted)
                            } else {
                                showRelease(release)
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            state.text =
                                "Update check failed: ${
                                    e.message ?: "unknown error"
                                }"
                        }
                    }
                }.start()
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(10)
            }
        )

        root.addView(c)
    }

    private fun maybeAskNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                700
            )
        }
    }
}
