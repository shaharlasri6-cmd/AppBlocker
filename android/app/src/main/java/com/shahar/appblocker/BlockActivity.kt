package com.shahar.appblocker

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.lang.ref.WeakReference

class BlockActivity : Activity() {
    companion object {
        private var current = WeakReference<BlockActivity>(null)

        fun closeActive() {
            current.get()?.let { activity ->
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.finishAndRemoveTask()
                }
            }
            current.clear()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = WeakReference(this)
        showBlock()
    }

    override fun onResume() {
        super.onResume()
        current = WeakReference(this)
        showBlock()
    }

    override fun onDestroy() {
        if (current.get() === this) {
            current.clear()
        }
        super.onDestroy()
    }

    private fun showBlock() {
        val target = intent.getStringExtra("pkg")

        if (target.isNullOrBlank() || target == packageName) {
            finishAndRemoveTask()
            return
        }

        val reason = intent.getStringExtra("reason") ?: "general"
        val store = PolicyStore(this)
        val rule = store.rule(target)

        val stillValid =
            (reason == "general" && rule.mode == "BLOCKED") ||
                (reason == "time" && rule.mode == "TIME_LIMITED")

        if (!stillValid) {
            finishAndRemoveTask()
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.rgb(8, 12, 18))
        }

        val path =
            if (reason == "time") store.imagePath("time")
            else store.imagePath("general")

        if (path.isNotBlank() && File(path).exists()) {
            val image = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                maxHeight = resources.displayMetrics.heightPixels / 2
                setImageURI(android.net.Uri.fromFile(File(path)))
            }
            box.addView(
                image,
                LinearLayout.LayoutParams(-1, 0, 1f)
            )
        } else {
            box.addView(
                TextView(this).apply {
                    text = if (reason == "time") "⏱" else "🔒"
                    textSize = 68f
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                },
                LinearLayout.LayoutParams(-1, 0, 1f)
            )
        }

        box.addView(
            TextView(this).apply {
                text =
                    if (reason == "time")
                        "Time limit reached"
                    else
                        "Application blocked"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
            },
            LinearLayout.LayoutParams(-1, -2)
        )

        box.addView(
            Button(this).apply {
                text = "Back to Home"
                isAllCaps = false
                setOnClickListener {
                    goHome()
                }
            },
            LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = 36
            }
        )

        setContentView(box)
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finishAndRemoveTask()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        goHome()
    }
}
