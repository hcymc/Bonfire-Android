package cn.hcy.gopan;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.security.*;
import java.util.*;

/** Real Android + real isolated gopan server integration suite; no mocked protocol. */
@android.annotation.TargetApi(35)
public class FlowTest extends Instrumentation {
    final ArrayList<String> checks=new ArrayList<>();Context c;Api api;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    void ok(boolean condition,String label){if(!condition)throw new AssertionError(label);checks.add(label);}
    @Override public void onStart(){Bundle result=new Bundle();try{
        c=getTargetContext();api=Api.get(c);api.clear();api.setBase("http://10.0.2.2:18787");
        for(String url:new String[]{"file:///etc/passwd","https://user:pw@example.com","https://example.com/api","https://example.com/?password=x"}){boolean rejected=false;try{Api.normalize(url);}catch(Exception e){rejected=true;}ok(rejected,"reject unsafe URL: "+url.split(":")[0]);}
        String username="android_"+System.currentTimeMillis()%10000000;
        JSONObject auth=api.request("/api/auth/register","POST",Api.json("username",username,"password","Gopan-Test-2026!"));ok(auth.has("user"),"register and encrypted login");ok(Vault.get(c).contains("cookie"),"Keystore session roundtrip");ok(api.request("/api/auth/me").getJSONObject("user").optString("username").equals(username),"authenticated cookie request");
        JSONObject dir=api.request("/api/files/folder","POST",Api.json("name","旅行与日常","parentId",null));int folder=dir.getInt("id");
        File input=new File(c.getCacheDir(),"integration.bin");try(OutputStream out=new FileOutputStream(input)){byte[] b=new byte[65536];new Random(42).nextBytes(b);for(int i=0;i<330;i++)out.write(b);}
        JSONObject up=TaskStore.get(c).add(Api.json("owner",api.owner(),"kind","upload","uri",Uri.fromFile(input).toString(),"name","山野记录.zip","mime","application/zip","size",input.length(),"done",0,"folder",null,"state","queued"));int uid=up.getInt("id");
        new Transfers.Runner(c,uid,t->{if(t.optLong("done")>0)Transfers.pause(c,uid);}).run();JSONObject paused=TaskStore.get(c).task(uid);ok(paused.optString("state").equals("paused"),"pause state survives worker checkpoint");ok(paused.optLong("done")>0&&paused.optLong("done")<input.length(),"first chunk persisted before pause");
        TaskStore.get(c).state(uid,"queued","");new Transfers.Runner(c,uid,t->{}).run();ok(TaskStore.get(c).task(uid).optString("state").equals("done"),"chunked upload resumes from server missing chunks");
        JSONArray files=api.request("/api/files").getJSONArray("files");ok(files.length()==1,"resume completes without duplicate file");JSONObject file=files.getJSONObject(0);
        JSONObject down=Transfers.download(c,file,"/api/files/"+file.getInt("id")+"/download");int did=down.getInt("id");File partial=new File(c.getFilesDir(),"transfer-"+did+".part");try(InputStream in=new FileInputStream(input);OutputStream out=new FileOutputStream(partial)){out.write(in.readNBytes(777777));}
        new Transfers.Runner(c,did,t->{}).run();JSONObject downloaded=TaskStore.get(c).task(did);ok(downloaded.optString("state").equals("done"),"Range download resumes an existing partial file");ok(Arrays.equals(hash(input),hash(new File(downloaded.getString("local")))),"download SHA-256 matches original 20.6 MB");
        api.request("/api/files/"+file.getInt("id"),"PATCH",Api.json("name","秋日山野记录.zip"));api.request("/api/files/"+file.getInt("id")+"/move","POST",Api.json("folderId",folder));ok(api.request("/api/files?folderId="+folder).getJSONArray("files").length()==1,"rename and move file");
        JSONObject share=api.request("/api/shares","POST",Api.json("fileId",file.getInt("id"),"expiresIn",86400,"password","Campfire8","maxHits",5,"visibility","private"));ok(share.has("token"),"create protected expiring share");ok(api.request("/api/shares").getJSONArray("shares").getJSONObject(0).optBoolean("needs_password"),"share password boolean contract");
        api.request("/api/files/"+file.getInt("id"),"DELETE",null);ok(api.request("/api/files/recycle/list").getJSONArray("files").length()==1,"delete goes to recycle");api.request("/api/files/recycle/"+file.getInt("id")+"/restore","POST",null);ok(api.request("/api/files/recycle/list").getJSONArray("files").length()==0,"restore recycled file");
        api.request("/api/me","PATCH",Api.json("displayName","山间来信","bio","把日常存好，把灵感带在身边。"));api.request("/api/guestbook","POST",Api.json("message","第一份文件已经抵达，来围炉聊聊今天的见闻。"));
        seedText("写给秋天的一封信.txt","风经过树林，云经过山顶。\n把今天的小事，好好收藏。\n",null);seedText("项目灵感.md","# 下一次出发\n- 带上相机\n- 记录沿途的光\n",null);api.request("/api/files/folder","POST",Api.json("name","工作与灵感","parentId",null));
        api.request("/api/files/"+file.getInt("id")+"/move","POST",Api.json("folderId",null));
        Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);MainActivity activity=(MainActivity)startActivitySync(intent);waitForIdleSync();Thread.sleep(2200);screenshot("01-files.png");
        JSONObject scheduled=Transfers.download(c,file,"/api/files/"+file.getInt("id")+"/download");runOnMainSync(()->{try{Transfers.schedule(activity,scheduled.optInt("id"));}catch(Exception e){throw new RuntimeException(e);}});
        long until=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<until&&!TaskStore.get(c).task(scheduled.optInt("id")).optString("state").equals("done"))Thread.sleep(500);ok(TaskStore.get(c).task(scheduled.optInt("id")).optString("state").equals("done"),"Android 15 UIDT JobService completes real download");
        openPage(1,"main");screenshot("02-transfers.png");openPage(0,"shares");screenshot("03-shares.png");openPage(3,"main");screenshot("04-messages.png");openPage(4,"main");screenshot("05-account.png");
        c.getSharedPreferences("settings",0).edit().putString("theme","dark").commit();openPage(0,"main");screenshot("06-dark.png");c.getSharedPreferences("settings",0).edit().putString("theme","system").commit();
        String oldOwner=api.owner();api.clear();ok(!api.owner().equals(oldOwner),"logout isolates previous transfer owner");openPage(0,"main");screenshot("00-login.png");
        result.putString("stream","PASS "+checks.size()+" checks\n"+String.join("\n",checks));finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAILED after "+checks.size()+" checks\n"+String.join("\n",checks)+"\n"+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}}
    byte[] hash(File file)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return digest.digest();}
    void openPage(int tab,String page)throws Exception{startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab",tab).putExtra("page",page));waitForIdleSync();Thread.sleep(1200);}
    void seedText(String name,String text,Integer folder)throws Exception{byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.UTF_8);JSONObject init=api.request("/api/uploads/init","POST",Api.json("name",name,"size",bytes.length,"folderId",folder));java.net.HttpURLConnection c=api.open("/api/uploads/"+init.getString("uploadId")+"/chunk/0","PUT");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/octet-stream");c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}api.response(c);c.disconnect();api.request("/api/uploads/"+init.getString("uploadId")+"/complete","POST",Api.json("mime","text/plain"));}
    void screenshot(String name)throws Exception{File dir=new File(c.getExternalFilesDir(null),"screenshots");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name))){Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("Screenshot unavailable");b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();}}
}
