package com.z7ui.routinex;

import android.content.Context;
import android.content.Intent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 真正把快捷方式拉起来的地方。
 *
 * 踩过的坑：比亚迪这类 App 的跳转 Activity 挂了 signature 级自定义权限
 * （com.byd.aeri.jump.permission），普通应用 startActivity 直接被拒。
 * 所以优先用 root 走 `am start`，root 能绕过该检查；失败再退回 startActivity。
 */
public class Launcher {

    public static boolean run(Context c, Routine r) {
        if (r.type == Routine.TYPE_APP) {
            try {
                Intent i = c.getPackageManager().getLaunchIntentForPackage(r.targetPackage);
                if (i == null) return false;
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        if (r.uri == null || r.uri.isEmpty()) return false;

        // 1) root 方式：最可靠
        if (runViaRoot(c, r)) return true;

        // 2) 退化：直接 startActivity（只对没有自定义权限的应用有效）
        try {
            Intent i = Intent.parseUri(r.uri, Intent.URI_INTENT_SCHEME);
            if (i.getPackage() == null && i.getComponent() == null
                    && r.targetPackage != null && !r.targetPackage.isEmpty()) {
                i.setPackage(r.targetPackage);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean runViaRoot(Context c, Routine r) {
        String uri = r.uri.replace("'", "%27");
        String cmd = "am start -a android.intent.action.VIEW -f 0x10000000 -d '" + uri + "'";
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            int code = p.waitFor();
            String out = bos.toString("UTF-8");
            Logx.i(c, "root am start exit=" + code + " out=" + out.trim().replace('\n', ' '));
            if (code != 0) return false;
            if (out.contains("Exception") || out.contains("Error:") || out.contains("blocked")
                    || out.contains("not started") || out.contains("Permission Denial")) {
                return false;
            }
            return out.contains("Status: ok") || out.contains("Starting:");
        } catch (Exception e) {
            Logx.i(c, "root am start 异常: " + e);
            return false;
        }
    }
}
