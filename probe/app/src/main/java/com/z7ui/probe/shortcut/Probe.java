package com.z7ui.probe.shortcut;

import android.content.Context;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 探针：验证普通应用（非启动器、无 root）能不能读和启动别人的快捷方式。
 * 结果既打到 logcat（tag=PROBE），也追加到 files/probe.log。
 */
public class Probe {

    public static String run(Context ctx, String mode, String pkg, String id) {
        StringBuilder sb = new StringBuilder();
        long t = System.currentTimeMillis();
        sb.append("[").append(fmt(t)).append("] mode=").append(mode)
                .append(" pkg=").append(pkg).append(" id=").append(id).append("\n");

        LauncherApps la = (LauncherApps) ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (la == null) {
            sb.append("  LauncherApps 服务为空\n");
            return finish(ctx, sb);
        }

        try {
            sb.append("  hasShortcutHostPermission=").append(la.hasShortcutHostPermission()).append("\n");
        } catch (Throwable e) {
            sb.append("  hasShortcutHostPermission 异常: ").append(describe(e)).append("\n");
        }

        if ("enum".equals(mode)) {
            try {
                LauncherApps.ShortcutQuery q = new LauncherApps.ShortcutQuery();
                q.setPackage(pkg);
                q.setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        | LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED);
                List<ShortcutInfo> list = la.getShortcuts(q, Process.myUserHandle());
                int n = list == null ? -1 : list.size();
                sb.append("  getShortcuts 成功，数量=").append(n).append("\n");
                if (list != null) {
                    for (ShortcutInfo si : list) {
                        CharSequence label = si.getShortLabel();
                        sb.append("    - id=").append(si.getId())
                                .append(" label=").append(label == null ? "(null)" : label).append("\n");
                    }
                }
            } catch (Throwable e) {
                sb.append("  getShortcuts 失败: ").append(describe(e)).append("\n");
            }
        } else {
            try {
                // Android 17（API 37）只保留了带 UserHandle 的重载
                la.startShortcut(pkg, id, null, null, Process.myUserHandle());
                sb.append("  startShortcut 返回正常（没有抛异常）\n");
            } catch (Throwable e) {
                sb.append("  startShortcut 失败: ").append(describe(e)).append("\n");
            }
        }

        return finish(ctx, sb);
    }

    private static String finish(Context ctx, StringBuilder sb) {
        String s = sb.toString();
        Log.i("PROBE", s);
        append(ctx, s);
        return s;
    }

    private static String describe(Throwable e) {
        String msg = e.getMessage();
        return e.getClass().getName() + (msg == null ? "" : ": " + msg);
    }

    private static String fmt(long t) {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(t));
    }

    private static void append(Context ctx, String s) {
        try {
            File f = new File(ctx.getFilesDir(), "probe.log");
            FileOutputStream out = new FileOutputStream(f, true);
            try {
                out.write(s.getBytes("UTF-8"));
                out.write("\n".getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
