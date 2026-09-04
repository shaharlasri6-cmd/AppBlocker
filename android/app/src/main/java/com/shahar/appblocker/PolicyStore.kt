package com.shahar.appblocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Rule(
    val mode: String,
    val allowance: Int = 0,
    val window: Int = 0
)

class PolicyStore(private val context: Context) {
    private val prefs =
        context.getSharedPreferences("local_policy_v2", Context.MODE_PRIVATE)

    fun rule(pkg: String): Rule {
        if (pkg == context.packageName) return Rule("UNRESTRICTED")
        val x = rulesObject().optJSONObject(pkg) ?: return Rule("UNRESTRICTED")
        return Rule(
            x.optString("mode", "UNRESTRICTED"),
            x.optInt("allowance_minutes", 0),
            x.optInt("window_minutes", 0)
        )
    }

    @Synchronized
    fun setRule(pkg: String, rule: Rule) {
        if (pkg == context.packageName) return
        val all = rulesObject()
        if (rule.mode == "UNRESTRICTED") {
            all.remove(pkg)
        } else {
            all.put(
                pkg,
                JSONObject()
                    .put("mode", rule.mode)
                    .put("allowance_minutes", rule.allowance)
                    .put("window_minutes", rule.window)
            )
        }
        prefs.edit().putString("rules", all.toString()).apply()
    }

    fun ruleCount(mode: String): Int {
        val all = rulesObject()
        var count = 0
        val keys = all.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (all.optJSONObject(key)?.optString("mode") == mode) count++
        }
        return count
    }

    @Synchronized
    fun clearAllRules() {
        prefs.edit().remove("rules").apply()
    }

    fun settings(): JSONObject =
        JSONObject()
            .put("general_image", prefs.getString("general_image", "") ?: "")
            .put("time_image", prefs.getString("time_image", "") ?: "")
            .put(
                "notification_thresholds",
                prefs.getString("notification_thresholds", "[15,10,5,1]")
                    ?: "[15,10,5,1]"
            )

    fun imagePath(kind: String): String =
        prefs.getString(if (kind == "time") "time_image" else "general_image", "")
            ?: ""

    fun setImagePath(kind: String, path: String) {
        prefs.edit()
            .putString(if (kind == "time") "time_image" else "general_image", path)
            .apply()
    }

    fun thresholds(): List<Int> {
        val a = try {
            JSONArray(
                prefs.getString("notification_thresholds", "[15,10,5,1]")
                    ?: "[15,10,5,1]"
            )
        } catch (_: Exception) {
            JSONArray("[15,10,5,1]")
        }
        return (0 until a.length())
            .mapNotNull { i -> a.optInt(i, -1).takeIf { it >= 0 } }
            .distinct()
            .sortedDescending()
    }

    fun setThresholds(values: List<Int>) {
        val cleaned = values.filter { it >= 0 }.distinct().sortedDescending()
        prefs.edit()
            .putString("notification_thresholds", JSONArray(cleaned).toString())
            .apply()
    }

    fun serverNow(): Long = System.currentTimeMillis() / 1000L

    private fun rulesObject(): JSONObject = try {
        JSONObject(prefs.getString("rules", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }
}
