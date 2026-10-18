package com.z7ui.routinex;

import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用 root 读系统的快捷方式数据，把「某个 App 有哪些快捷方式、各自的 intent URI 是什么」挖出来。
 *
 * 数据位置：/data/system_ce/0/shortcut_service/packages/&lt;包名&gt;.xml
 * 这是个二进制封装的文件，但里面的 URI 是明文字符串，直接扫出来即可。
 */
public class ShortcutScanner {

    public static class Shot {
        public final String label;
        public final String uri;
        public final String path;

        public Shot(String label, String uri, String path) {
            this.label = label;
            this.uri = uri;
            this.path = path;
        }
    }

    private static final Pattern LABEL_IN_PAYLOAD = Pattern.compile("\"2\"\\s*:\\s*\"([^\"]{1,40})\"");

    public static boolean hasRoot() {
        try {
            return su("id").contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Shot> scan(String pkg) {
        List<Shot> result = new ArrayList<>();
        String file = "/data/system_ce/0/shortcut_service/packages/" + pkg + ".xml";
        byte[] raw;
        try {
            raw = suBytes("cat " + file);
        } catch (Exception e) {
            return result;
        }
        if (raw == null || raw.length == 0) return result;

        String s = new String(raw, StandardCharsets.ISO_8859_1);
        Set<String> seen = new LinkedHashSet<>();
        int from = 0;
        while (true) {
            int start = s.indexOf("scheme_", from);
            if (start < 0) break;
            int end = start;
            while (end < s.length()) {
                char ch = s.charAt(end);
                if (ch < 0x20 || ch > 0x7e) break;
                end++;
            }
            String rawUri = s.substring(start, end);
            from = Math.max(end, start + 1);

            String uri = rawUri;
            String flags = "";
            int hash = rawUri.indexOf("#Intent;");
            if (hash > 0) {
                uri = rawUri.substring(0, hash);
                String tail = rawUri.substring(hash);
                int f = tail.indexOf("launchFlags=");
                if (f >= 0) {
                    int semi = tail.indexOf(';', f);
                    flags = semi > 0 ? tail.substring(f + 12, semi) : "";
                }
            }
            if (uri.isEmpty() || !seen.add(uri)) continue;

            String label = labelFrom(uri);
            String path = pathFrom(uri);
            String full = uri;
            if (!flags.isEmpty()) {
                full = uri + "#Intent;launchFlags=" + flags + ";end";
            }
            result.add(new Shot(label.isEmpty() ? path : label, full, path));
        }
        return result;
    }

    private static String labelFrom(String uri) {
        try {
            String decoded = URLDecoder.decode(uri, "UTF-8");
            // 负载里的 JSON 是转义过的（\"2\":\"每日签到\"），先把反斜杠去掉再匹配
            String flat = decoded.replace("\\", "");
            Matcher m = LABEL_IN_PAYLOAD.matcher(flat);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String pathFrom(String uri) {
        try {
            String decoded = URLDecoder.decode(uri, "UTF-8");
            Matcher m = Pattern.compile("uxwvMjt1HC=([^&]+)").matcher(decoded);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
        }
        try {
            String p = Uri.parse(uri).getPath();
            if (p == null) return "快捷方式";
            if (p.startsWith("/path_")) p = p.substring(6);
            p = p.replace('/', ' ').replace('_', ' ').trim();
            return p.isEmpty() ? "快捷方式" : p;
        } catch (Exception e) {
            return "快捷方式";
        }
    }

    // ---------- root 执行 ----------

    private static String su(String cmd) throws Exception {
        byte[] out = suBytes(cmd);
        return out == null ? "" : new String(out, StandardCharsets.UTF_8);
    }

    private static byte[] suBytes(String cmd) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
        InputStream in = p.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        p.waitFor();
        return bos.toByteArray();
    }
}
