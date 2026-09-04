package com.shahar.appblocker
import android.content.Context
import org.json.*
import java.net.HttpURLConnection
import java.net.URL
class ApiClient(private val c:Context){private val p=c.getSharedPreferences("device",Context.MODE_PRIVATE)
 fun base()=p.getString("server","")!!.trimEnd('/')
 fun paired()=!p.getString("token",null).isNullOrBlank()
 fun deviceId()=p.getString("id","")!!
 private fun req(method:String,path:String,body:JSONObject?=null,auth:Boolean=false):JSONObject{val u=URL(base()+path);val h=u.openConnection() as HttpURLConnection;h.requestMethod=method;h.connectTimeout=5000;h.readTimeout=8000;h.setRequestProperty("Content-Type","application/json");if(auth){h.setRequestProperty("Authorization","Bearer "+p.getString("token",""));h.setRequestProperty("X-Device-Id",deviceId())};if(body!=null){h.doOutput=true;h.outputStream.use{it.write(body.toString().toByteArray())}};val s=(if(h.responseCode in 200..299)h.inputStream else h.errorStream).bufferedReader().readText();if(h.responseCode !in 200..299)throw Exception(s);return JSONObject(s)}
 fun startPair(server:String):JSONObject{p.edit().putString("server",server.trimEnd('/')).apply();val id=p.getString("id",null)?:java.util.UUID.randomUUID().toString().also{p.edit().putString("id",it).apply()};return req("POST","/api/device/pairing/start",JSONObject().put("device_id",id).put("name",android.os.Build.MODEL).put("manufacturer",android.os.Build.MANUFACTURER).put("model",android.os.Build.MODEL).put("android_version",android.os.Build.VERSION.RELEASE))}
 fun pairStatus(code:String):JSONObject{val r=req("GET","/api/device/pairing/status/"+code.replace("-",""));if(r.optBoolean("approved")){p.edit().putString("token",r.getString("device_token")).apply()};return r}
 fun policy()=req("GET","/api/device/policy",auth=true)
 fun post(path:String,j:JSONObject)=req("POST",path,j,true)
}