package com.z7ui.routinex;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * 执行结果校验。
 *
 * <p>为什么需要：`am start` 成功只代表「快捷方式被系统接受了」，不代表签到真的完成。
 * 实测过两次（10-25、10-27）：日志写着执行成功，用户那边却是漏签。
 *
 * <p>做法：拉起之后等页面渲染，用 root 跑一次 `uiautomator dump`，把界面文字抓下来，
 * 看有没有「已签到 / 签到成功」这类完成标记，或者「立即签到」这类未完成按钮。
 * 两个都没有就返回「无法判断」——宁可承认不知道，也不谎报成功。
 */
public class Verifier {

    private static final String DUMP_PATH = "/sdcard/rx_verify.xml";
    /** 完成标记：出现任意一个就算签到成功。 */
    private static final String[] DONE = {"已签到", "签到成功", "今日已签", "已连续签到"};
    /** 未完成标记：出现就说明页面还停在「去签到」。 */
    private static final String[] TODO = {"立即签到", "点击签到", "去签到"};

    private static final long WAIT_MS = 2500L;

    /** 单次校验：1=成功，2=未完成，0=判断不了。 */
    public static int checkOnce(Context c) {
        sleep(WAIT_MS);
        String xml = dump(c);
        if (xml == null || xml.isEmpty()) {
            Logx.i(c, "校验：拿不到界面，判为无法判断");
            return Runner.UNKNOWN;
        }
        boolean done = containsAny(xml, DONE);
        boolean todo = containsAny(xml, TODO);
        Logx.i(c, "校验：done=" + done + " todo=" + todo + " len=" + xml.length());
        if (done) return Runner.OK;
        if (todo) return Runner.FAIL;
        return Runner.UNKNOWN;
    }

    private static String dump(Context c) {
        // uiautomator 的输出是文件，root 读回来；stderr 里的 "UI hierchary dumped" 不要
        String cmd = "uiautomator dump --compressed " + DUMP_PATH + " >/dev/null 2>&1; "
                + "cat " + DUMP_PATH;
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            InputStream in = p.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            p.waitFor();
            return bos.toString("UTF-8");
        } catch (Throwable e) {
            Logx.i(c, "校验：dump 失败 " + e);
            return null;
        }
    }

    private static boolean containsAny(String text, String[] keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
