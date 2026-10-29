package com.z7ui.routinex;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 执行历史：每天一条，保留最近 30 条。
 *
 * <p>为什么不复用 rx.log：日志是「排查用」的，会被清、会被轮转，而且默认关着；
 * 历史是「对账用」的，必须一直在，用户随时能回答「这个月漏了几次」。
 *
 * <p>存储是一行一条的纯文本（{@code ts|result|source|title|note}），
 * 不做数据库——这个量级用不上，出问题还能直接用 cat 看。
 */
public class History {

    private static final String FILE = "rx.history";
    private static final int MAX = 30;

    public static synchronized void add(Context c, Routine r, String source, long ts, int result, String note) {
        try {
            List<String> lines = readAll(c);
            lines.add(ts + "|" + result + "|" + safe(source) + "|" + safe(title(r)) + "|" + safe(note));
            while (lines.size() > MAX) {
                lines.remove(0);
            }
            File f = new File(c.getFilesDir(), FILE);
            FileOutputStream out = new FileOutputStream(f, false);
            try {
                for (String line : lines) {
                    out.write((line + "\n").getBytes("UTF-8"));
                }
            } finally {
                out.close();
            }
        } catch (Throwable e) {
            Logx.i(c, "写执行历史失败 " + e);
        }
    }

    /** 最近 n 条，新的在前，已经格式化好，直接能显示。 */
    public static synchronized List<String> recentLines(Context c, int n) {
        List<String> all = readAll(c);
        List<String> out = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.US);
        for (int i = all.size() - 1; i >= 0 && out.size() < n; i--) {
            String[] p = split(all.get(i));
            if (p == null) continue;
            long ts = parseLong(p[0]);
            int result = (int) parseLong(p[1]);
            String label = result == Runner.OK ? "成功" : result == Runner.FAIL ? "失败" : "未确认";
            out.add(fmt.format(new Date(ts)) + "  " + label + "  " + p[2] + "  " + p[3] + "\n    " + p[4]);
        }
        return out;
    }

    public static synchronized void clear(Context c) {
        new File(c.getFilesDir(), FILE).delete();
    }

    private static List<String> readAll(Context c) {
        List<String> lines = new ArrayList<>();
        File f = new File(c.getFilesDir(), FILE);
        if (!f.exists()) return lines;
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty()) lines.add(line);
                }
            } finally {
                br.close();
            }
        } catch (Throwable ignored) {
        }
        return lines;
    }

    private static String[] split(String line) {
        String[] p = line.split("\\|", 5);
        return p.length == 5 ? p : null;
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable e) {
            return 0L;
        }
    }

    private static String title(Routine r) {
        if (r.title != null && !r.title.isEmpty()) return r.title;
        if (r.shortcutLabel != null && !r.shortcutLabel.isEmpty()) return r.shortcutLabel;
        return r.targetLabel == null ? "" : r.targetLabel;
    }

    /** 竖线是分隔符，正文里不能出现。 */
    private static String safe(String s) {
        return s == null ? "" : s.replace('|', '/').replace('\n', ' ');
    }
}
