package cn.hcy.gopan;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.util.List;

final class Notices {
    static void channels(Context c) {
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("transfer","传输进度",NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel("result","传输结果",NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel("messages","新留言与公告",NotificationManager.IMPORTANCE_DEFAULT));
    }
    static Transfers.Progress updates(Context c) {
        Context app=c.getApplicationContext();
        return task -> TransferCenter.update(app, task);
    }
    /** 单任务一张卡片，或整批任务共用一张卡片。 */
    static Notification aggregate(Context c, List<JSONObject> tasks) {
        if (tasks.size()==1) return transfer(c, tasks.get(0));
        channels(c);
        int[] palette={0xffD95D39,0xff3675A9,0xff527F65,0xff8065AA};
        int color=palette[Math.max(0,Math.min(3,c.getSharedPreferences("settings",0).getInt("accent",0)))];
        long done=0,total=0,speed=0,upDone=0,upTotal=0,downDone=0,downTotal=0;
        int uploads=0,downloads=0,saving=0,waiting=0;
        JSONObject front=null; long frontStamp=-1;
        for (JSONObject t:tasks) {
            boolean uploading=t.optString("kind").equals("upload");
            String state=t.optString("state");
            if (uploading) uploads++; else downloads++;
            if (state.equals("queued")) waiting++;
            long size=t.optLong("size"),d=t.optLong("done");
            if (size<=0) continue;
            long realDone=Math.min(d,size);
            done+=realDone; total+=size; speed+=t.optLong("speed");
            if (uploading) { upDone+=realDone; upTotal+=size; } else { downDone+=realDone; downTotal+=size; }
            if (realDone>=size) saving++;
            long stamp=t.optLong("stamp",-1);
            if (state.equals("running")&&stamp>frontStamp) { frontStamp=stamp; front=t; }
        }
        boolean idle=waiting==tasks.size();
        boolean allSaving=saving==tasks.size()&&!idle;
        boolean indeterminate=total<=0||allSaving;
        int pct=indeterminate?0:(int)Math.min(100,done*100/total);
        String title=idle?"等待传输 "+tasks.size()+" 项":allSaving?"正在保存 "+tasks.size()+" 个文件"
            :"正在传输 "+tasks.size()+" 项"+(indeterminate?"":" · "+pct+"%");
        String mix=uploads==0?"共 "+downloads+" 个下载":downloads==0?"共 "+uploads+" 个上传"
            :"上传 "+uploads+" · 下载 "+downloads;
        String text=front!=null&&!idle&&!allSaving?compactName(front.optString("name")):mix;
        String sub=idle?total>0?MainActivity.bytes(total):""
            :speed>0?MainActivity.bytes(speed)+"/s · 剩余 "+TransferRate.time(TransferRate.remaining(total-done,done,speed)):mix;
        PendingIntent content=PendingIntent.getActivity(c,TransferCenter.NOTIFICATION_ID,new Intent(c,MainActivity.class).putExtra("tab",1),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause=PendingIntent.getBroadcast(c,TransferCenter.NOTIFICATION_ID,new Intent(c,TransferAction.class).putExtra("id",-1),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        boolean uploadingNow=front!=null?front.optString("kind").equals("upload"):uploads>=downloads;
        Notification.Builder b=new Notification.Builder(c,"transfer")
            .setSmallIcon(uploadingNow?R.drawable.ic_upload:R.drawable.ic_download)
            .setColor(color).setShowWhen(false)
            .setLargeIcon(TransferArtwork.icon(front==null?"":front.optString("name"),color))
            .setContentTitle(title).setContentText(text).setSubText(sub)
            .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(true)
            .setCategory(Notification.CATEGORY_PROGRESS).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setProgress(100,pct,indeterminate)
            .addAction(new Notification.Action.Builder(null,"全部暂停",pause).build());
        if (Build.VERSION.SDK_INT>=31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        promote(b,!idle&&(front!=null||speed>0)?allSaving?"保存":indeterminate?null:pct+"%":null);
        // 双色进度：进度条按上传/下载的字节占比分段。
        int split=total>0&&upTotal>0&&downTotal>0?(int)Math.max(1,Math.min(99,upTotal*100/total)):0;
        applyProgressStyle(b,pct,indeterminate,color,split,0);
        return b.build();
    }
    static Notification transfer(Context c,JSONObject t) {
        channels(c);int id=t.optInt("id");long done=t.optLong("done"),size=t.optLong("size");int pct=size>0?(int)Math.min(100,done*100/size):0;
        Intent open=new Intent(c,MainActivity.class).putExtra("tab",1);
        PendingIntent content=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pause=PendingIntent.getBroadcast(c,id,new Intent(c,TransferAction.class).putExtra("id",id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        boolean uploading=t.optString("kind").equals("upload");
        int icon=uploading?R.drawable.ic_upload:R.drawable.ic_download;
        int[] palette={0xffD95D39,0xff3675A9,0xff527F65,0xff8065AA};
        int color=palette[Math.max(0,Math.min(3,c.getSharedPreferences("settings",0).getInt("accent",0)))];
        String name=t.optString("name");
        boolean active=t.optString("state").equals("running");
        boolean finalizing=active&&size>0&&done>=size;
        String title=finalizing?"正在保存文件":active?(uploading?"上传":"下载")+TransferArtwork.label(name)+" · "+pct+"%":"等待传输";
        long speed=t.optLong("speed");
        String detail=active&&speed>0&&!finalizing?MainActivity.bytes(speed)+"/s · 剩余 "+TransferRate.time(t.optLong("eta",-1)):MainActivity.bytes(done)+" / "+MainActivity.bytes(size);
        Notification.Builder b=new Notification.Builder(c,"transfer").setSmallIcon(icon).setColor(color).setShowWhen(false)
            .setLargeIcon(TransferArtwork.icon(name,color))
            .setContentTitle(title)
            .setContentText(compactName(name))
            .setSubText(detail)
            .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(true).setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setProgress(100,pct,size==0||finalizing).addAction(new Notification.Action.Builder(null,"暂停",pause).build());
        if(Build.VERSION.SDK_INT>=31)b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        promote(b,active?finalizing?"保存":pct+"%":null);
        applyProgressStyle(b,pct,size==0||finalizing,color,0,0);
        return b.build();
    }
    /** Android 16 live-update markers; runtime reflection keeps compileSdk 35 happy. */
    private static void promote(Notification.Builder b,String shortText) {
        if (Build.VERSION.SDK_INT<36||shortText==null)return;
        try { Notification.Builder.class.getMethod("setRequestPromotedOngoing",boolean.class).invoke(b,true); }
        catch(ReflectiveOperationException ignored) { b.getExtras().putBoolean("android.extra.requestPromotedOngoing",true); }
        if (shortText!=null) try { Notification.Builder.class.getMethod("setShortCriticalText",String.class).invoke(b,shortText); } catch(ReflectiveOperationException ignored) { }
    }
    /** ProgressStyle with optional second segment; no private OEM APIs. */
    private static void applyProgressStyle(Notification.Builder b,int pct,boolean indeterminate,int color,int split,int secondColor) {
        if (Build.VERSION.SDK_INT<36)return;
        try {
            Class<?> styleClass=Class.forName("android.app.Notification$ProgressStyle");
            Notification.Style style=(Notification.Style)styleClass.getConstructor().newInstance();
            styleClass.getMethod("setProgress",int.class).invoke(style,pct);
            styleClass.getMethod("setProgressIndeterminate",boolean.class).invoke(style,indeterminate);
            Class<?> segmentClass=Class.forName("android.app.Notification$ProgressStyle$Segment");
            java.util.List<Object> segments=new java.util.ArrayList<>();
            if (split>0&&split<100) {
                int dimmed=0x66000000|color&0x00FFFFFF;
                segments.add(segment(segmentClass,split,color));
                segments.add(segment(segmentClass,100-split,secondColor!=0?secondColor:dimmed));
            } else segments.add(segment(segmentClass,100,color));
            styleClass.getMethod("setProgressSegments",java.util.List.class).invoke(style,segments);
            b.setStyle(style);
        }catch(ReflectiveOperationException ignored){ }
    }
    private static Object segment(Class<?> segmentClass,int weight,int color) throws ReflectiveOperationException {
        Object s=segmentClass.getConstructor(int.class).newInstance(weight);
        segmentClass.getMethod("setColor",int.class).invoke(s,color);
        return s;
    }
    static String compactName(String name) {
        int count=name.codePointCount(0,name.length());
        if(count<=26)return name;
        return name.substring(0,name.offsetByCodePoints(0,12))+"…"+name.substring(name.offsetByCodePoints(0,count-11));
    }
    static void result(Context c,JSONObject t,boolean success) {
        channels(c);c.getSystemService(NotificationManager.class).notify(t.optInt("id"),new Notification.Builder(c,"result")
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(success?"传输完成":"传输需要处理").setContentText(t.optString("name")+(success?"": " · "+t.optString("error")))
            .setAutoCancel(true).setContentIntent(PendingIntent.getActivity(c,t.optInt("id"),new Intent(c,MainActivity.class).putExtra("tab",1),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE)).build());
    }
}
