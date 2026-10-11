package cn.hcy.gopan;
import android.app.*;
import android.content.Intent;
import android.os.*;
import org.json.JSONObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

public class TransferService extends Service {
    private final ExecutorService executor=Executors.newSingleThreadExecutor();private final AtomicInteger count=new AtomicInteger();
    public IBinder onBind(Intent i){return null;}
    public void onCreate(){super.onCreate();TransferCenter.foregroundServices.incrementAndGet();}
    public int onStartCommand(Intent i,int flags,int start){
        if(i==null){stopSelf();return START_NOT_STICKY;}int id=i.getIntExtra("id",0);JSONObject t=TaskStore.get(this).task(id);if(t==null){stopSelf(start);return START_NOT_STICKY;}
        startForeground(TransferCenter.NOTIFICATION_ID,TransferCenter.build(this,t));count.incrementAndGet();
        executor.execute(()->{new Transfers.Runner(this,id,Notices.updates(this)).run();if(count.decrementAndGet()==0){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}});
        return START_NOT_STICKY;
    }
    public void onTimeout(int startId,int fgsType){for(Transfers.Runner r:Transfers.running.values())r.stop();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
    public void onDestroy(){TransferCenter.foregroundServices.decrementAndGet();for(Transfers.Runner r:Transfers.running.values())r.stop();executor.shutdownNow();super.onDestroy();}
}
