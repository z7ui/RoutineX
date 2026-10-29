package com.z7ui.routinex;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 执行结果校验。
 *
 * <p>为什么需要：`am start` 成功只代表「快捷方式被系统接受了」，不代表签到真的完成。
 * 实测过两次（10-25、10-27）：日志写着执行成功，用户那边却是漏签。
 *
 * <p>这个类前后改过两次，两次都是真机测出来的，记在这里免得再走回头路：
 *
 * <ol>
 *   <li><b>固定等 2.5 秒不够</b>。签到页是 WebView，2.5 秒时只抓到空壳
 *       （814 字节，而加载完是 67 KB），关键词一个都碰不上 → 永远「无法判断」。
 *       现在改成轮询：先等 2.5 秒给首帧，之后每 1.5 秒抓一次，最多 18 秒，
 *       一旦有明确结论立刻返回。</li>
 *
 *   <li><b>页面上根本没有「已签到」「立即签到」这些字</b>。真实文案是
 *       「已连续签到 N 天」，今天签没签只体现在<b>日历上今天那一格下面有没有一个勾</b>。
 *       所以判据改成看日历：找到文字为「今日」的格子，看它下面 90px 内有没有节点。
 *       同一页可以横向对照——漏签的日子（25、27）下面什么都没有，签过的（26、28）才有。
 *       关键词只作为日历结构认不出来时的兜底。</li>
 * </ol>
 */
public class Verifier {

    /** 放在 /data/local/tmp：uiautomator 以 shell 身份跑，这里可读可写，不依赖存储权限。 */
    private static final String DUMP_PATH = "/data/local/tmp/rx_verify.xml";

    /** 完成标记：日历认不出来时才用。 */
    private static final String[] DONE = {"已签到", "签到成功", "今日已签", "已连续签到"};
    /** 未完成标记：页面还停在「去签到」。 */
    private static final String[] TODO = {"立即签到", "点击签到", "去签到"};

    /** 首次等待：WebView 首帧通常 2~4 秒。 */
    private static final long FIRST_WAIT_MS = 2500L;
    /** 两轮之间的间隔。 */
    private static final long POLL_MS = 1500L;
    /** 总上限：超过就认「判断不了」，不要一直拖着通知栏。 */
    private static final long MAX_WAIT_MS = 18000L;

    private static final Pattern NODE = Pattern.compile("<node\\b[^>]*>");
    private static final Pattern A_TEXT = Pattern.compile("\\btext=\"([^\"]*)\"");
    private static final Pattern A_BOUNDS =
            Pattern.compile("\\bbounds=\"\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]\"");

    /** 单次校验：{@link Runner#OK} / {@link Runner#FAIL} / {@link Runner#UNKNOWN}。 */
    public static int checkOnce(Context c) {
        sleep(FIRST_WAIT_MS);
        long deadline = System.currentTimeMillis() + MAX_WAIT_MS;
        int round = 0;
        int lastLen = 0;

        while (System.currentTimeMillis() < deadline) {
            round++;
            String xml = dump(c);
            if (xml == null || xml.length() < 800) {
                // 空壳：页面还没渲染出来，继续等，别急着下结论
                sleep(POLL_MS);
                continue;
            }
            lastLen = xml.length();
            int verdict = judge(c, xml);
            if (verdict != Runner.UNKNOWN) {
                Logx.i(c, "校验：第 " + round + " 轮判定=" + Runner.label(verdict)
                        + " len=" + xml.length());
                return verdict;
            }
            sleep(POLL_MS);
        }

        Logx.i(c, "校验：" + round + " 轮后仍无法判断 len=" + lastLen);
        return Runner.UNKNOWN;
    }

    /** 先看日历（准），认不出来再看关键词（糙）。 */
    private static int judge(Context c, String xml) {
        Integer byCalendar = judgeByCalendar(c, xml);
        if (byCalendar != null) {
            return byCalendar;
        }
        boolean done = containsAny(xml, DONE);
        boolean todo = containsAny(xml, TODO);
        Logx.i(c, "校验：日历没认出来，退回关键词 done=" + done + " todo=" + todo);
        if (done) return Runner.OK;
        if (todo) return Runner.FAIL;
        return Runner.UNKNOWN;
    }

    /**
     * 读日历：今天（文字是「今日」的那一格）下面有没有签到标记。
     *
     * @return 有勾 {@link Runner#OK}、没勾 {@link Runner#FAIL}、认不出这一格 {@code null}
     */
    private static Integer judgeByCalendar(Context c, String xml) {
        // 页面数据还没填进来时，「今日」可能已经在、勾却还没画出来，这时不能判失败。
        // 「补签卡」「连续签到」「温馨提示」任意一个出现，说明这块是渲染完的状态。
        if (!xml.contains("连续签到") && !xml.contains("补签卡") && !xml.contains("温馨提示")) {
            return null;
        }

        List<Node> nodes = nodes(xml);
        Node today = null;
        for (Node n : nodes) {
            if ("今日".equals(n.text)) {
                today = n;
                break;
            }
        }
        if (today == null || today.w() <= 0) {
            return null;
        }

        // 在「今日」这一格正下方找节点：y 落在 [下边界, 下边界+90px]，x 与格子中心重叠
        int hits = 0;
        StringBuilder sample = new StringBuilder();
        for (Node n : nodes) {
            if (n == today || n.w() <= 0 || n.h() <= 0) {
                continue;
            }
            if (n.top < today.bottom - 4 || n.top > today.bottom + 90) {
                continue;
            }
            if (Math.abs(n.centerX() - today.centerX()) > 70) {
                continue;
            }
            hits++;
            if (hits <= 3) {
                // 把命中的节点打出来：万一签到页改版、下方换成别的装饰，能一眼看出认错了
                sample.append('[').append(n.text).append(']');
            }
        }
        Logx.i(c, "校验：今日格 " + today.left + "," + today.top + "-" + today.bottom
                + " 下方节点数=" + hits + " " + sample);
        return hits > 0 ? Runner.OK : Runner.FAIL;
    }

    private static List<Node> nodes(String xml) {
        List<Node> out = new ArrayList<>();
        Matcher m = NODE.matcher(xml);
        while (m.find()) {
            String tag = m.group();
            Matcher b = A_BOUNDS.matcher(tag);
            if (!b.find()) {
                continue;
            }
            Matcher t = A_TEXT.matcher(tag);
            String text = t.find() ? t.group(1) : "";
            try {
                out.add(new Node(text,
                        Integer.parseInt(b.group(1)), Integer.parseInt(b.group(2)),
                        Integer.parseInt(b.group(3)), Integer.parseInt(b.group(4))));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static final class Node {
        final String text;
        final int left, top, right, bottom;

        Node(String text, int left, int top, int right, int bottom) {
            this.text = text;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        int w() {
            return right - left;
        }

        int h() {
            return bottom - top;
        }

        int centerX() {
            return (left + right) / 2;
        }
    }

    private static String dump(Context c) {
        // 不加 --compressed：压缩会丢掉部分文本，签到页的字都在 TextView 里，宁可慢一点
        String cmd = "uiautomator dump " + DUMP_PATH + " >/dev/null 2>&1; "
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
