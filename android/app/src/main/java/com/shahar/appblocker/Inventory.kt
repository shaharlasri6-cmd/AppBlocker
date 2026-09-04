package com.shahar.appblocker
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object Inventory {
    private var last = 0L
    fun sync(c: Context, api: ApiClient) {
        if (System.currentTimeMillis() - last < 10 * 60 * 1000) return
        last = System.currentTimeMillis()
        val arr = JSONArray()
        for (x in c.packageManager.getInstalledApplications(0)) {
            if (x.packageName == c.packageName) continue
            val icon = try { encodeIcon(c.packageManager.getApplicationIcon(x)) } catch (_: Exception) { null }
            arr.put(JSONObject()
                .put("package_name", x.packageName)
                .put("app_name", c.packageManager.getApplicationLabel(x).toString())
                .put("system_app", (x.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                .put("icon_b64", icon))
        }
        api.post("/api/device/inventory", JSONObject().put("apps", arr))
    }
    private fun encodeIcon(d: Drawable): String {
        val b = if (d is BitmapDrawable && d.bitmap != null) d.bitmap else Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { d.setBounds(0, 0, 96, 96); d.draw(Canvas(it)) }
        val out = ByteArrayOutputStream(); b.compress(Bitmap.CompressFormat.PNG, 70, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
