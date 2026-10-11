package cn.hcy.gopan;

/** Monotonic sampling of acknowledged bytes; resumed bytes never count as new traffic. */
final class TransferRate {
    private long previousBytes, previousTime;
    private boolean started;
    private double rate;

    long sample(long bytes, long now) {
        if (!started || bytes < previousBytes || now < previousTime) {
            started = true;
            previousBytes = bytes;
            previousTime = now;
            rate = 0;
            return 0;
        }
        long elapsed = now - previousTime;
        if (elapsed < 1000) return Math.round(rate);
        double measured = Math.max(0, bytes - previousBytes) * 1000.0 / elapsed;
        rate = rate == 0 ? measured : .65 * rate + .35 * measured;
        previousBytes = bytes;
        previousTime = now;
        return Math.round(rate);
    }

    static long remaining(long size, long done, long rate) {
        if (size <= 0 || rate <= 0) return -1;
        long bytes = Math.max(0, size - done);
        return bytes / rate + (bytes % rate == 0 ? 0 : 1);
    }

    static String time(long seconds) {
        if (seconds < 0) return "正在计算";
        if (seconds < 60) return "约 " + Math.max(1, seconds) + " 秒";
        long minutes = (seconds + 59) / 60;
        if (minutes < 60) return "约 " + minutes + " 分钟";
        return "约 " + minutes / 60 + " 小时 " + minutes % 60 + " 分";
    }
}
