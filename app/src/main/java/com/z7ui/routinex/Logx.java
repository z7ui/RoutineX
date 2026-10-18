package com.z7ui.routinex;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 可选的运行日志，默认关闭（设置里手动打开）。
 * 排查问题时打开，翻日志：
 *   adb shell run-as com.z7ui.routinex cat files/rx.log
 */
public class Logx {

    private static final Object LOCK = new Object();
    private static final long MAX = 256 * 1024;

    public static void i(Context c, String msg) {
        if (c == null || !Prefs.logEnabled(c)) return;
        write(c, msg);
    }

    private static void write(Context c, String msg) {
        try {
            synchronized (LOCK) {
                File f = new File(c.getFilesDir(), "rx.log");
                if (f.length() > MAX) {
                    File old = new File(c.getFilesDir(), "rx.old.log");
                    if (old.exists()) old.delete();
                    f.renameTo(old);
                    f = new File(c.getFilesDir(), "rx.log");
                }
                String line = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                        .format(new Date()) + "  " + msg + "\n";
                FileOutputStream out = new FileOutputStream(f, true);
                try {
                    out.write(line.getBytes("UTF-8"));
                } finally {
                    out.close();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 日志当前占用字节数（不存在则为 0）。 */
    public static long size(Context c) {
        long n = 0;
        for (String name : new String[]{"rx.log", "rx.old.log"}) {
            File f = new File(c.getFilesDir(), name);
            if (f.exists()) n += f.length();
        }
        return n;
    }

    /** 删除全部日志，返回释放的字节数。 */
    public static long clear(Context c) {
        long freed = 0;
        for (String name : new String[]{"rx.log", "rx.old.log"}) {
            File f = new File(c.getFilesDir(), name);
            if (f.exists()) {
                freed += f.length();
                f.delete();
            }
        }
        return freed;
    }

    public static String sizeText(Context c) {
        long n = size(c);
        if (n <= 0) return "暂无日志";
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.US, "%.1f KB", n / 1024f);
        return String.format(Locale.US, "%.1f MB", n / 1024f / 1024f);
    }
}
