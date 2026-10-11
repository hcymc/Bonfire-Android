package cn.hcy.gopan;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import android.net.*;
import android.os.*;
import android.provider.OpenableColumns;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class Transfers {
    static final ConcurrentHashMap<Integer,Runner> running=new ConcurrentHashMap<>();
    static final Semaphore slots=new Semaphore(2,true);
    /** 已启动（可见）的界面计数——大于 0 表示用户正看着应用。 */
    static final AtomicInteger foreground=new AtomicInteger();
    static JSONObject upload(Context c,Uri uri,Integer folder) throws Exception {
        String name="",mime=c.getContentResolver().getType(uri);long size=-1;
        try(Cursor q=c.getContentResolver().query(uri,null,null,null,null)) { if(q!=null&&q.moveToFirst()){int n=q.getColumnIndex(OpenableColumns.DISPLAY_NAME),s=q.getColumnIndex(OpenableColumns.SIZE);if(n>=0)name=q.getString(n);if(s>=0&&!q.isNull(s))size=q.getLong(s);} }
        if(size<=0||name==null||name.isEmpty())throw new IOException("该文件无法确定大小或为空，请先保存到本机后上传");
        return TaskStore.get(c).add(Api.json("owner",Api.get(c).owner(),"kind","upload","uri",uri.toString(),"name",name,"mime",mime==null?"application/octet-stream":mime,"size",size,"done",0,"folder",folder,"state","queued"));
    }
    static JSONObject download(Context c,JSONObject file,String path) throws Exception {
        return TaskStore.get(c).add(Api.json("owner",Api.get(c).owner(),"kind","download","path",path,"name",file.optString("name"),"mime",file.optString("mime","application/octet-stream"),"size",file.optLong("size"),"done",0,"state","queued"));
    }
    static void schedule(Context c,int id) throws Exception {
        JSONObject t=TaskStore.get(c).task(id);if(t==null||!t.optString("owner").equals(Api.get(c).owner()))throw new IOException("任务不属于当前账户");
        if(running.containsKey(id))throw new IOException("任务正在收尾，请稍后再恢复");
        TaskStore.get(c).state(id,"queued","");
        if(Build.VERSION.SDK_INT>=34){
            boolean wifi=c.getSharedPreferences("settings",0).getBoolean("wifi",false);
            NetworkRequest.Builder network=new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if(wifi)network.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
            JobInfo job=new JobInfo.Builder(id,new ComponentName(c,TransferJob.class)).setUserInitiated(true).setRequiredNetwork(network.build())
                .setEstimatedNetworkBytes(t.optString("kind").equals("download")?Math.max(1,t.optLong("size")):0,t.optString("kind").equals("upload")?Math.max(1,t.optLong("size")):0).build();
        if(c.getSystemService(JobScheduler.class).schedule(job)!=JobScheduler.RESULT_SUCCESS){TaskStore.get(c).state(id,"failed","系统暂未允许启动，请在前台重试");throw new IOException("系统暂未允许启动任务");}
        if(foreground.get()>0)runNow(c,id);
        TransferCenter.refresh(c);
    }else{c.startForegroundService(new Intent(c,TransferService.class).putExtra("id",id));TransferCenter.refresh(c);}
    }

    /**
     * 立即在进程内执行任务，完全绕过系统调度器。
     *
     * 调度器可能把已接受的用户发起任务无限期压着——运行时长配额（此前没有看门狗时
     * 的卡死会把它烧光）、厂商省电策略、反复断网后的退避，都会让用户盯着传输页
     * 干等。应用在前台时进程本来就活着，直接跑就行。与系统调度路径并存是安全的：
     * 任务已有 Job 在跑时 Runner.run() 会被 running.putIfAbsent 挡掉。进程被杀后
     * 残留的「运行中」任务也由这里接管。
     */
    static void runNow(Context c,int id){
        try{
            JSONObject t=TaskStore.get(c).task(id);
            if(t==null||!t.optString("owner").equals(Api.get(c).owner()))return;
            String st=t.optString("state");
            if(!st.equals("queued")&&!st.equals("running"))return;
            if(running.containsKey(id))return;
            TaskStore.get(c).state(id,"queued","");
            new Thread(()->new Runner(c.getApplicationContext(),id,Notices.updates(c)).run(),"gopan-direct-"+id).start();
        }catch(Exception ignored){}
    }
    static void pause(Context c,int id) {
        TaskStore.get(c).state(id,"paused","");Runner r=running.get(id);if(r!=null)r.stop();
        c.getSystemService(JobScheduler.class).cancel(id);c.getSystemService(NotificationManager.class).cancel(id);
        TransferCenter.forget(c,id);
    }
    static void pauseOwner(Context c) { for(JSONObject t:TaskStore.get(c).all(Api.get(c).owner()))if(t.optString("state").equals("running")||t.optString("state").equals("queued"))pause(c,t.optInt("id")); }
    static void cancel(Context c,int id) throws Exception {
        pause(c,id);long until=System.currentTimeMillis()+5000;while(running.containsKey(id)&&System.currentTimeMillis()<until)Thread.sleep(100);if(running.containsKey(id))throw new IOException("任务正在停止，请稍后再次取消");JSONObject t=TaskStore.get(c).task(id);
        if(t!=null&&!t.optString("uploadId").isEmpty()) { try{Api.get(c).request("/api/uploads/"+t.optString("uploadId"),"DELETE",null);}catch(Api.Failure e){if(e.status!=404)throw e;} }
        TaskStore.get(c).state(id,"cancelled","");new File(c.getFilesDir(),"transfer-"+id+".part").delete();
    }
    interface Progress { void update(JSONObject t); }
    static final class Runner {
        final Context c;final int id;final TaskStore store;final Api api;final Progress listener;final AtomicBoolean stopped=new AtomicBoolean();
        volatile HttpURLConnection active;volatile long lastProgress=0;long lastSave=0;JSONObject t;
        ScheduledExecutorService watchdog=null;
        final TransferRate rate = new TransferRate();
        Runner(Context c,int id,Progress p){this.c=c.getApplicationContext();this.id=id;store=TaskStore.get(c);api=Api.get(c);listener=p;}
        void stop(){stopped.set(true);HttpURLConnection conn=active;if(conn!=null)conn.disconnect();}
        void check() throws IOException {if(stopped.get()||!t.optString("owner").equals(api.owner()))throw new InterruptedIOException("已暂停");}
        void save() throws Exception {
            check();
            long speed=rate.sample(t.optLong("done"),SystemClock.elapsedRealtime());
            t.put("speed",speed);
            t.put("eta",TransferRate.remaining(t.optLong("size"),t.optLong("done"),speed));
            if(!store.saveProgress(t))throw new InterruptedIOException("已暂停");listener.update(t);lastSave=System.currentTimeMillis();
        }
        void run(){
            if(running.putIfAbsent(id,this)!=null)return;
            PowerManager.WakeLock lock=c.getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"gopan:transfer");
            boolean acquired=false;try {
                t=store.task(id);if(t==null||t.optString("state").equals("paused")||t.optString("state").equals("cancelled"))return;
                while(!(acquired=slots.tryAcquire(1,TimeUnit.SECONDS)))check();
                check();lock.acquire(60*60*1000L);t.put("state","running");t.put("error","");save();
                // HttpURLConnection 没有"写超时"：网络半死（WiFi/流量切换、NAT 失效）时
                // 分片 PUT 的 write() 会在 TCP 重传窗口里阻塞十几分钟，期间既不报错也不释放
                // 并发槽位，其他任务全部卡在"等待网络/系统调度"。看门狗在 60s 无任何字节
                // 进展时强制断开当前连接，把僵死转成正常的重试/失败路径（慢速但活跃的连接
                // 不会误伤——按字节进展计时，不按耗时）。
                lastProgress=SystemClock.elapsedRealtime();
                watchdog=Executors.newSingleThreadScheduledExecutor(r->{Thread w=new Thread(r,"gopan-progress-"+id);w.setDaemon(true);return w;});
                watchdog.scheduleAtFixedRate(()->{if(SystemClock.elapsedRealtime()-lastProgress>60000){HttpURLConnection c=active;if(c!=null)c.disconnect();}},15,15,TimeUnit.SECONDS);
                if(Build.VERSION.SDK_INT<34&&c.getSharedPreferences("settings",0).getBoolean("wifi",false)){
                    ConnectivityManager cm=c.getSystemService(ConnectivityManager.class);NetworkCapabilities caps=cm.getNetworkCapabilities(cm.getActiveNetwork());
                    if(caps==null||!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))throw new IOException("等待 Wi-Fi，请连接后恢复");
                }
                Exception failure=null;
                for(int attempt=0;attempt<3;attempt++){
                    try { check();lastProgress=SystemClock.elapsedRealtime();if(t.optString("kind").equals("upload"))upload();else download();failure=null;break; }
                    catch(Exception e){failure=e;if(stopped.get()||e instanceof Api.Failure&&((Api.Failure)e).status<500)break;if(attempt<2){t.put("error","网络中断，正在重试 "+(attempt+1)+"/2");save();Thread.sleep(1000L*(attempt+1));}}
                }
                if(failure!=null)throw failure;check();t.put("state","done");t.put("done",t.optLong("size"));t.put("error","");if(store.saveProgress(t))Notices.result(c,t,true);
            }catch(Exception e){
                if(t!=null&&!stopped.get()){try{t.put("state","failed");t.put("error",e instanceof Api.Failure&&((Api.Failure)e).status==401?"登录已过期，重新登录后恢复":e.getMessage()==null?"连接中断，请重试":e.getMessage());if(store.saveProgress(t))Notices.result(c,t,false);}catch(Exception ignored){}}
            }finally{if(active!=null)active.disconnect();if(watchdog!=null)watchdog.shutdownNow();if(lock.isHeld())lock.release();if(acquired)slots.release();running.remove(id,this);TransferCenter.forget(c,id);}
        }
        void upload() throws Exception {
            if(t.optString("uploadId").isEmpty()){
                JSONObject init=api.request("/api/uploads/init","POST",Api.json("name",t.optString("name"),"size",t.optLong("size"),"folderId",t.isNull("folder")?null:t.optInt("folder")));
                t.put("uploadId",init.getString("uploadId"));t.put("chunkSize",init.getInt("chunkSize"));store.checkpoint(t);save();
            }
            JSONObject status;
            try{status=api.request("/api/uploads/"+t.optString("uploadId"));}catch(Api.Failure e){if(e.status==404){t.remove("uploadId");t.put("done",0);save();upload();return;}throw e;}
            if(status.optString("status").equals("completed"))return;
            if(!status.optString("status").equals("open"))throw new IOException("上传会话已关闭，请取消后重新上传");
            int chunk=t.optInt("chunkSize");if(chunk<=0)throw new IOException("无效分片尺寸");
            Set<Integer> received=new HashSet<>();JSONArray a=status.optJSONArray("receivedChunks");if(a!=null)for(int i=0;i<a.length();i++)received.add(a.getInt(i));
            long size=t.optLong("size"),position=0,done=status.optLong("received");t.put("done",done);save();
            try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(t.optString("uri")))){
                if(in==null)throw new IOException("无法读取原文件，请重新选择");
                byte[] buffer=new byte[64*1024];int index=0;
                while(position<size){check();int length=(int)Math.min(chunk,size-position);
                    if(received.contains(index)){skip(in,length);position+=length;index++;continue;}
                    active=api.open("/api/uploads/"+t.optString("uploadId")+"/chunk/"+index,"PUT");active.setDoOutput(true);active.setRequestProperty("Content-Type","application/octet-stream");active.setFixedLengthStreamingMode(length);
                    int remaining=length;
                    try(OutputStream out=active.getOutputStream()){
                        while(remaining>0){check();int n=in.read(buffer,0,Math.min(buffer.length,remaining));if(n<0)throw new IOException("原文件大小已改变");out.write(buffer,0,n);remaining-=n;lastProgress=SystemClock.elapsedRealtime();}
                    }
                    JSONObject response=api.response(active);active.disconnect();active=null;done=response.optLong("received",done+length);t.put("done",done);save();position+=length;index++;
                }
                if(in.read()!=-1)throw new IOException("原文件大小已改变，请重新上传");
            }
            check();api.request("/api/uploads/"+t.optString("uploadId")+"/complete","POST",Api.json("mime",t.optString("mime")));
        }
        static void skip(InputStream in,long count)throws IOException{while(count>0){long n=in.skip(count);if(n>0)count-=n;else{if(in.read()==-1)throw new IOException("文件已改变");count--;}}}
        void download() throws Exception {
            File dir=new File(c.getFilesDir(),"downloads");if(!dir.exists()&&!dir.mkdirs())throw new IOException("无法创建下载目录");
            File part=new File(c.getFilesDir(),"transfer-"+id+".part");long offset=part.length(),expected=t.optLong("size");
            if(offset<expected||expected==0){
                active=api.open(t.optString("path"),"GET");active.setRequestProperty("Accept","*/*");if(offset>0)active.setRequestProperty("Range","bytes="+offset+"-");
                int code=active.getResponseCode();if(code!=200&&code!=206){api.response(active);throw new IOException("下载响应无效");}
                if(code==200)offset=0;
                if(code==206&&(active.getHeaderField("Content-Range")==null||!active.getHeaderField("Content-Range").startsWith("bytes "+offset+"-")))throw new IOException("服务器断点响应不一致");
                long length=active.getContentLengthLong();if(expected==0&&length>=0){expected=offset+length;t.put("size",expected);}
                try(InputStream in=active.getInputStream();RandomAccessFile out=new RandomAccessFile(part,"rw")){
                    if(offset==0)out.setLength(0);out.seek(offset);byte[] b=new byte[64*1024];int n;
                    while((n=in.read(b))!=-1){check();out.write(b,0,n);offset+=n;t.put("done",offset);if(System.currentTimeMillis()-lastSave>600)save();}
                    out.getFD().sync();
                }active.disconnect();active=null;
                if(expected>0&&offset!=expected)throw new IOException("下载不完整，将从断点重试");t.put("size",offset);t.put("done",offset);save();
            }
            check();String name=t.optString("name").replaceAll("[\\\\/\\p{Cntrl}]","_");if(name.isEmpty())name="download";
            File dest=new File(dir,id+"-"+name);if(!part.renameTo(dest)){if(!dest.isFile()||dest.length()!=t.optLong("size"))throw new IOException("无法保存下载文件");}
            t.put("local",dest.getAbsolutePath());save();
        }
    }
}
