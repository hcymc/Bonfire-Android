package cn.hcy.gopan;
import android.app.job.*;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.util.concurrent.ConcurrentHashMap;

public class TransferJob extends JobService {
    private final ConcurrentHashMap<Integer,Transfers.Runner> runners=new ConcurrentHashMap<>();
    public boolean onStartJob(JobParameters p){
        if(android.os.Build.VERSION.SDK_INT<34)return false;
        JSONObject t=TaskStore.get(this).task(p.getJobId());if(t==null)return false;
        setNotification(p,TransferCenter.NOTIFICATION_ID,TransferCenter.build(this,t),JOB_END_NOTIFICATION_POLICY_DETACH);
        Transfers.Runner runner=new Transfers.Runner(this,p.getJobId(),Notices.updates(this));
        runners.put(p.getJobId(),runner);
        new Thread(()->{runner.run();if(runners.remove(p.getJobId(),runner))new Handler(Looper.getMainLooper()).post(()->jobFinished(p,false));},"gopan-transfer-"+p.getJobId()).start();return true;
    }
    public boolean onStopJob(JobParameters p){
        Transfers.Runner r=runners.remove(p.getJobId());if(r!=null)r.stop();JSONObject t=TaskStore.get(this).task(p.getJobId());
        boolean retry=t!=null&&!t.optString("state").equals("paused")&&!t.optString("state").equals("cancelled");
        if(retry)TaskStore.get(this).state(p.getJobId(),"queued","等待网络或系统调度");return retry;
    }
}
