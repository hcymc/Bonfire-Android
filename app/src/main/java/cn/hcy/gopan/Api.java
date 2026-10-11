package cn.hcy.gopan;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class Api {
    private static Api instance;
    final Context context;
    private String base, cookie = "", csrf = "", identity = "";
    static synchronized Api get(Context c) { if (instance == null) instance = new Api(c.getApplicationContext()); return instance; }
    private Api(Context c) {
        context = c; base = c.getSharedPreferences("settings", 0).getString("server", "");
        JSONObject session = parse(Vault.get(c)); cookie = session.optString("cookie"); csrf = session.optString("csrf"); identity = session.optString("identity");
    }
    static JSONObject json(Object... kv) {
        JSONObject o = new JSONObject(); try { for (int i = 0; i < kv.length; i += 2) o.put((String)kv[i], kv[i+1] == null ? JSONObject.NULL : kv[i+1]); } catch (JSONException e) { throw new IllegalArgumentException(e); } return o;
    }
    static JSONObject parse(String text) { try { return new JSONObject(text); } catch (JSONException e) { return new JSONObject(); } }
    static String normalize(String raw) throws Exception {
        URI u = new URI(raw.trim());
        if (!("https".equals(u.getScheme()) || "http".equals(u.getScheme())) || u.getHost() == null || u.getUserInfo() != null || u.getQuery() != null || u.getFragment() != null) throw new IOException("填写完整的 http:// 或 https:// 服务器地址");
        String path = u.getPath(); if (path != null && !path.isEmpty() && !path.equals("/")) throw new IOException("填写服务器根地址，不要包含 /api 或子路径");
        return new URI(u.getScheme(), null, u.getHost(), u.getPort(), null, null, null).toString();
    }
    synchronized String base() { return base; }
    synchronized String owner() { return base + "|" + identity; }
    synchronized boolean loggedIn() { return !cookie.isEmpty(); }
    synchronized Map<String,String> mediaHeaders() { Map<String,String> headers=new HashMap<>();if(!cookie.isEmpty())headers.put("Cookie",cookie);return headers; }
    synchronized void setBase(String url) throws Exception {
        String b = normalize(url); if (!b.equals(base)) { clear(); base = b; context.getSharedPreferences("settings", 0).edit().putString("server", b).commit(); }
    }
    synchronized void clear() { cookie = ""; csrf = ""; identity = ""; Vault.clear(context); }
    synchronized void persist() throws Exception { Vault.put(context, json("cookie", cookie, "csrf", csrf, "identity", identity).toString()); }
    HttpURLConnection open(String path, String method) throws Exception {
        String b; String cookies; String token;
        synchronized (this) { b = base; cookies = cookie; token = csrf; }
        if (b.isEmpty()) throw new IOException("请先设置服务器");
        if (!path.startsWith("/api/")) throw new IOException("无效接口路径");
        HttpURLConnection conn = (HttpURLConnection)new URL(b + path).openConnection();
        conn.setInstanceFollowRedirects(false); conn.setRequestMethod(method); conn.setConnectTimeout(15000); conn.setReadTimeout(45000);
        conn.setRequestProperty("Accept", "application/json");
        if (!cookies.isEmpty()) conn.setRequestProperty("Cookie", cookies);
        if (!method.equals("GET") && !token.isEmpty()) conn.setRequestProperty("X-CSRF-Token", token);
        return conn;
    }
    JSONObject request(String path) throws Exception { return request(path, "GET", null); }
    JSONObject request(String path, String method, JSONObject body) throws Exception {
        HttpURLConnection conn = open(path, method);
        try {
            if (body != null) {
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8); conn.setDoOutput(true); conn.setRequestProperty("Content-Type", "application/json"); conn.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = conn.getOutputStream()) { out.write(bytes); }
            }
            JSONObject result = response(conn);
            synchronized (this) {
                List<String> cookies = new ArrayList<>();
                for (Map.Entry<String,List<String>> entry : conn.getHeaderFields().entrySet()) if ("set-cookie".equalsIgnoreCase(entry.getKey())) cookies.addAll(entry.getValue());
                Map<String,String> jar = new LinkedHashMap<>();
                for (String part : cookie.split("; ")) { int n = part.indexOf('='); if(n>0) jar.put(part.substring(0,n), part.substring(n+1)); }
                for (String part : cookies) { String first = part.split(";",2)[0]; int n = first.indexOf('='); if (n>0) jar.put(first.substring(0,n), first.substring(n+1)); }
                List<String> pairs = new ArrayList<>();
                for (Map.Entry<String,String> entry : jar.entrySet()) if (!entry.getValue().isEmpty()) pairs.add(entry.getKey()+"="+entry.getValue());
                cookie = String.join("; ", pairs);
                if (result.has("csrf")) csrf = result.optString("csrf");
                if (result.has("user")) identity = String.valueOf(result.getJSONObject("user").optLong("id"));
                if (!cookie.isEmpty()) persist();
            }
            return result;
        } finally { conn.disconnect(); }
    }
    JSONObject response(HttpURLConnection conn) throws Exception {
        int status = conn.getResponseCode(); InputStream in = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String text = in == null ? "" : new String(readLimited(in, 4 * 1024 * 1024), StandardCharsets.UTF_8);
        JSONObject o = parse(text);
        if (status < 200 || status >= 300) throw new Failure(status, o.optString("error", "服务器返回 " + status));
        if (!text.isEmpty() && (conn.getContentType()==null || !conn.getContentType().contains("json"))) throw new IOException("地址未返回云盘接口，请检查服务器地址");
        return o;
    }
    static byte[] readLimited(InputStream in, int max) throws IOException {
        try (in; ByteArrayOutputStream out = new ByteArrayOutputStream()) { byte[] b = new byte[8192]; int n; while ((n=in.read(b)) != -1) { if (out.size()+n>max) throw new IOException("内容过大，请下载后打开"); out.write(b,0,n); } return out.toByteArray(); }
    }
    static final class Failure extends IOException { final int status; Failure(int s, String message) { super(message); status = s; } }
}
