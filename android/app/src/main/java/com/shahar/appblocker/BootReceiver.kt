package com.shahar.appblocker
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class BootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){c.startForegroundService(Intent(c,GuardService::class.java))}}