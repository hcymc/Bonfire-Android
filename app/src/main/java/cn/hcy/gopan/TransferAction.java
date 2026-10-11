package cn.hcy.gopan;
import android.content.*;
public class TransferAction extends BroadcastReceiver {
    public void onReceive(Context c,Intent i){
        int id=i.getIntExtra("id",0);
        if(id==-1)Transfers.pauseOwner(c);else Transfers.pause(c,id);
        TransferCenter.refresh(c.getApplicationContext());
    }
}
