package cn.hcy.gopan;

import android.graphics.*;
import java.util.Locale;

/** Small cached vector-like file illustrations for the system's large-icon slot. */
final class TransferArtwork {
    private static final android.util.LruCache<String, Bitmap> cache = new android.util.LruCache<>(24);

    private static String kind(String name) {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return ext.matches("mp4|mov|mkv|webm|avi") ? "video"
            : ext.matches("png|jpg|jpeg|gif|webp|heic|avif") ? "image"
            : ext.matches("mp3|m4a|flac|wav|ogg|aac") ? "audio"
            : ext.matches("zip|rar|7z|tar|gz") ? "archive" : "document";
    }

    static String label(String name) {
        switch (kind(name)) {
            case "video": return "视频";
            case "image": return "图片";
            case "audio": return "音频";
            case "archive": return "压缩包";
            default: return "文件";
        }
    }

    static synchronized Bitmap icon(String name, int accent) {
        String kind = kind(name);
        String key = kind + accent;
        Bitmap existing = cache.get(key);
        if (existing != null) return existing;
        Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(accent);
        canvas.drawRoundRect(4, 4, 124, 124, 30, 30, paint);
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(5);
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (kind.equals("video")) {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(31, 39, 97, 89, 9, 9, paint);
            paint.setStyle(Paint.Style.FILL);
            Path play = new Path(); play.moveTo(57, 52); play.lineTo(76, 64); play.lineTo(57, 76); play.close();
            canvas.drawPath(play, paint);
        } else if (kind.equals("audio")) {
            float[] heights = {14, 29, 43, 26, 16};
            for (int i=0; i<heights.length; i++) canvas.drawLine(38+i*13,64-heights[i]/2,38+i*13,64+heights[i]/2,paint);
        } else if (kind.equals("image")) {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(30, 34, 98, 94, 9, 9, paint);
            Path mountains = new Path(); mountains.moveTo(35,85); mountains.lineTo(54,65); mountains.lineTo(68,78); mountains.lineTo(79,65); mountains.lineTo(94,84);
            canvas.drawPath(mountains, paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(77,51,6,paint);
        } else {
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(41, 29, 88, 99, 6, 6, paint);
            if (kind.equals("archive")) for (int y=40; y<91; y+=10) canvas.drawLine(63,y,67,y,paint);
            else { canvas.drawLine(52,58,77,58,paint); canvas.drawLine(52,70,77,70,paint); canvas.drawLine(52,82,69,82,paint); }
        }
        cache.put(key, bitmap);
        return bitmap;
    }
}
