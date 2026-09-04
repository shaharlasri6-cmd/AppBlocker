package com.shahar.appblocker
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
sealed class SetupStep(val title:String,val detail:String,val intent:Intent?)
class SimpleStep(title:String,detail:String,intent:Intent?):SetupStep(title,detail,intent)
interface DeviceCompatProvider{fun steps(c:Context):List<SetupStep>}
object OemCompat{fun provider():DeviceCompatProvider{val m=(android.os.Build.MANUFACTURER+" "+android.os.Build.BRAND).lowercase();return when{m.contains("xiaomi")||m.contains("redmi")||m.contains("poco")->XiaomiCompat;m.contains("samsung")->SamsungCompat;else->GenericCompat}}}
object GenericCompat:DeviceCompatProvider{override fun steps(c:Context)=listOf(SimpleStep("Enable Accessibility","Required for immediate foreground-app detection.",Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),SimpleStep("Grant Usage Access","Required for usage accounting.",Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)),SimpleStep("Allow background reliability","Disable battery optimization for the device protection service if Android offers the option.",Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)))}
object SamsungCompat:DeviceCompatProvider{override fun steps(c:Context)=GenericCompat.steps(c)+listOf(SimpleStep("Samsung battery settings","In Device Care → Battery, add the device protection service to Never sleeping apps and disable automatic permission removal for it.",Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))))}
object XiaomiCompat:DeviceCompatProvider{override fun steps(c:Context)=GenericCompat.steps(c)+listOf(SimpleStep("Enable Autostart","In HyperOS/MIUI app settings, enable Autostart for the device protection service.",Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))),SimpleStep("Battery: No restrictions","Set the device protection service's battery saver mode to No restrictions and, if your version offers it, lock the device protection service in Recents.",Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${c.packageName}"))))}