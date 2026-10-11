package cn.hcy.gopan;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 所有进行中传输共用一张实时卡片。每个任务的进度都汇入这里，由一条通知
 * （带防抖）统一展示，代替此前批量上传下载时系统堆出的一排流体云条目。
 */
final class TransferCenter {
    static final int NOTIFICATION_ID = 90000;
    /** 持有卡片的前台服务计数；大于 0 时卡片不可以被取消。 */
    static final AtomicInteger foregroundServices = new AtomicInteger();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Map<Integer, JSONObject> live = new ConcurrentHashMap<>();
    private static boolean scheduled = false;
    private static long lastPost = 0;

    private TransferCenter() {}

    /** 记录任务的最新快照，并安排一次带防抖的卡片重建。 */
    static synchronized void update(Context c, JSONObject task) {
        JSONObject snapshot = Api.parse(task.toString());
        try {
            snapshot.put("stamp", SystemClock.elapsedRealtime());
        } catch (org.json.JSONException ignored) {
        }
        live.put(snapshot.optInt("id"), snapshot);
        schedule(c.getApplicationContext());
    }

    /** 丢弃任务的实时快照（已暂停、已取消或已完成），并立即刷新。 */
    static void forget(Context c, int id) {
        live.remove(id);
        refresh(c.getApplicationContext());
    }

    private static synchronized void schedule(final Context app) {
        if (scheduled) return;
        scheduled = true;
        long delay = Math.max(0, 500 - (SystemClock.elapsedRealtime() - lastPost));
        main.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        scheduled = false;
                        lastPost = SystemClock.elapsedRealtime();
                        refresh(app);
                    }
                },
                delay);
    }

    /** 用任务存储加上运行中任务的快照重建共用卡片。 */
    static void refresh(Context c) {
        List<JSONObject> active = activeTasks(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (active.isEmpty()) {
            if (foregroundServices.get() == 0) nm.cancel(NOTIFICATION_ID);
            return;
        }
        nm.notify(NOTIFICATION_ID, Notices.aggregate(c, active));
    }

    /** 当前要展示的通知；任务存储还没来得及更新时回退到传入的任务。 */
    static Notification build(Context c, JSONObject fallback) {
        List<JSONObject> active = activeTasks(c);
        if (active.isEmpty()) active = java.util.Collections.singletonList(fallback);
        return Notices.aggregate(c, active);
    }

    static List<JSONObject> activeTasks(Context c) {
        Map<Integer, JSONObject> active = new LinkedHashMap<>();
        for (JSONObject t : TaskStore.get(c).all(Api.get(c).owner())) {
            String state = t.optString("state");
            if (state.equals("queued") || state.equals("running")) active.put(t.optInt("id"), t);
        }
        for (JSONObject t : live.values()) {
            String state = t.optString("state");
            if (state.equals("queued") || state.equals("running")) active.put(t.optInt("id"), t);
        }
        return new ArrayList<>(active.values());
    }
}
