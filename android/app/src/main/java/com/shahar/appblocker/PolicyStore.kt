package com.shahar.appblocker
import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject

data class Rule(val mode:String,val allowance:Int=0,val window:Int=0)
class PolicyStore(private val c:Context){
 private val p=c.getSharedPreferences("policy",Context.MODE_PRIVATE)
 fun savePolicy(j:JSONObject){p.edit().putString("json",j.toString()).putLong("serverTime",j.optLong("server_time")).putLong("elapsedAtSync",SystemClock.elapsedRealtime()).apply()}
 fun raw()=p.getString("json","{}")?:"{}"
 fun rule(pkg:String):Rule{val a=JSONObject(raw()).optJSONArray("policies")?:JSONArray();for(i in 0 until a.length()){val x=a.getJSONObject(i);if(x.optString("package_name")==pkg)return Rule(x.optString("mode"),x.optInt("allowance_minutes"),x.optInt("window_minutes"))};return Rule("UNRESTRICTED")}
 fun settings():JSONObject=JSONObject(raw()).optJSONObject("settings")?:JSONObject()
 fun serverNow():Long{val s=p.getLong("serverTime",0);val e=p.getLong("elapsedAtSync",0);return if(s>0)s+(SystemClock.elapsedRealtime()-e)/1000 else System.currentTimeMillis()/1000}
 fun version()=try{JSONObject(raw()).optLong("version",0)}catch(_:Exception){0}
}