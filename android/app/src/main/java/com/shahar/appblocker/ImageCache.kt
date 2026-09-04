package com.shahar.appblocker
import android.content.Context
import org.json.JSONObject
import java.net.URL
object ImageCache{fun sync(c:Context,api:ApiClient,s:JSONObject){for(k in listOf("general_image","time_image")){val path=s.optString(k);if(path.isBlank())continue;try{val f=java.io.File(c.filesDir,k+if(path.endsWith("png"))".png" else ".jpg");URL(api.base()+path).openStream().use{input->f.outputStream().use{input.copyTo(it)}};c.getSharedPreferences("images",Context.MODE_PRIVATE).edit().putString(path,f.toURI().toString()).apply()}catch(_:Exception){}}}}