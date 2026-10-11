package cn.hcy.gopan;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.os.*;
import org.json.*;

/** Optional, system-scheduled polling. This is deliberately not described as instant push. */
public class MessageJob extends JobService {
    static final int JOB=80000;
    volatile boolean stopped;
    static void configure(Context c,boolean enabled){JobScheduler scheduler=c.getSystemService(JobScheduler.class);if(!enabled){scheduler.cancel(JOB);return;}scheduler.schedule(new JobInfo.Builder(JOB,new ComponentName(c,MessageJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());}
    public boolean onStartJob(JobParameters params){stopped=false;new Thread(()->{try{Api api=Api.get(this);if(!api.loggedIn())return;String owner=api.owner();SharedPreferences prefs=getSharedPreferences("message-cursors",0);JSONObject result=api.request("/api/guestbook?limit=100"),site=api.request("/api/site");if(stopped||!owner.equals(api.owner()))return;JSONArray messages=result.optJSONArray("messages");long newest=messages!=null&&messages.length()>0?messages.optJSONObject(0).optLong("id"):0;long previous=prefs.getLong(owner+".id",-1);String announcement=site.optString("announcement"),old=prefs.getString(owner+".announcement",null);boolean changed=old!=null&&!old.equals(announcement)&&!announcement.isEmpty();if(previous>=0&&(newest>previous||changed)){Notices.channels(this);String text=changed?announcement:"云盘有新留言，点击查看";Notification n=new Notification.Builder(this,"messages").setSmallIcon(R.drawable.ic_notification).setContentTitle(changed?"站点公告已更新":"留言板有新消息").setContentText(text).setAutoCancel(true).setContentIntent(PendingIntent.getActivity(this,JOB,new Intent(this,MainActivity.class).putExtra("tab",3),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build();getSystemService(NotificationManager.class).notify(JOB,n);}prefs.edit().putLong(owner+".id",newest).putString(owner+".announcement",announcement).apply();}catch(Exception ignored){/* Retry at the next system-scheduled interval; no persistent service. */}finally{if(!stopped)new Handler(Looper.getMainLooper()).post(()->jobFinished(params,false));}},"gopan-message-check").start();return true;}
    public boolean onStopJob(JobParameters params){stopped=true;return true;}
}

