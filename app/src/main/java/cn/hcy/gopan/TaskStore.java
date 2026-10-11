package cn.hcy.gopan;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

final class TaskStore extends SQLiteOpenHelper {
    private static TaskStore singleton;
    static synchronized TaskStore get(Context c) { if(singleton == null) singleton = new TaskStore(c.getApplicationContext()); return singleton; }
    private TaskStore(Context c) { super(c, "transfers.db", null, 1); }
    public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT, payload TEXT NOT NULL)"); }
    public void onUpgrade(SQLiteDatabase db,int old,int next) { throw new IllegalStateException("Unsupported migration"); }
    synchronized JSONObject add(JSONObject task) throws Exception {
        ContentValues v=new ContentValues(); v.put("payload",task.toString()); long id=getWritableDatabase().insertOrThrow("tasks",null,v); task.put("id",id); save(task); return task;
    }
    synchronized void save(JSONObject task) { ContentValues v=new ContentValues();v.put("payload",task.toString());getWritableDatabase().update("tasks",v,"id=?",new String[]{task.optString("id")}); }
    synchronized JSONObject task(int id) { try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM tasks WHERE id=?",new String[]{""+id})){return c.moveToFirst()?Api.parse(c.getString(0)):null;} }
    synchronized List<JSONObject> all(String owner) { List<JSONObject> list=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM tasks ORDER BY id DESC",null)){while(c.moveToNext()){JSONObject t=Api.parse(c.getString(0));if(t.optString("owner").equals(owner))list.add(t);}}return list; }
    synchronized void state(int id,String state,String error) { JSONObject t=task(id);if(t!=null){try{t.put("state",state);t.put("error",error);}catch(Exception ignored){}save(t);} }
    synchronized boolean saveProgress(JSONObject task) { JSONObject stored=task(task.optInt("id"));if(stored==null)return false;String state=stored.optString("state");if(state.equals("paused")||state.equals("cancelled")){try{task.put("state",state);task.put("error",stored.optString("error"));}catch(JSONException ignored){}save(task);return false;}save(task);return true; }
    synchronized void checkpoint(JSONObject task) { JSONObject stored=task(task.optInt("id"));if(stored==null)return;try{String state=stored.optString("state");if(state.equals("paused")||state.equals("cancelled"))task.put("state",state);}catch(JSONException ignored){}save(task); }
    synchronized void clearFinished(String owner) { for(JSONObject t:all(owner))if(t.optString("state").equals("done")||t.optString("state").equals("cancelled"))getWritableDatabase().delete("tasks","id=?",new String[]{t.optString("id")}); }
}
