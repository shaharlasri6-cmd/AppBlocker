package com.shahar.appblocker
import android.content.Context
class UsageLedger(c:Context){private val p=c.getSharedPreferences("usage",Context.MODE_PRIVATE)
 fun windowStart(now:Long,windowMin:Int):Long{val w=windowMin*60L;return if(w<=0)now else now-now%w}
 fun seconds(pkg:String,start:Long)=p.getLong("$pkg@$start",0)
 fun add(pkg:String,start:Long,sec:Long){if(sec<=0)return;p.edit().putLong("$pkg@$start",seconds(pkg,start)+sec).apply()}
 fun snapshot():List<Triple<String,Long,Long>> = p.all.mapNotNull{(k,v)->val i=k.lastIndexOf('@');if(i<=0||v !is Long)null else Triple(k.substring(0,i),k.substring(i+1).toLongOrNull()?:0,v)}.takeLast(1000)
}