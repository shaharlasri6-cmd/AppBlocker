package com.shahar.appblocker

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class BlockActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showBlock()
    }

    override fun onResume() {
        super.onResume()
        showBlock()
    }

    private fun showBlock() {
        val target = intent.getStringExtra("pkg")
        if (target.isNullOrBlank() || target == packageName) {
            finish()
            return
        }

        val reason = intent.getStringExtra("reason") ?: "general"
        val store = PolicyStore(this)

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

        setContentView(box)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        moveTaskToBack(true)
    }
}
