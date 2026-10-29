package com.z7ui.routinex;

import android.content.Context;

/**
 * 统一执行入口：拉起快捷方式 → 校验结果 → 记一条历史。
 *
 * <p>以前三个调用点（闹钟、解锁服务、测试按钮）各写各的，只有「拉起成功/失败」，
 * 于是出现了「日志写成功、实际没签到」的盲区。现在所有路径都走这里。
 */
public class Runner {

    /** 确认成功。 */
    public static final int OK = 1;
    /** 确认失败（拉起失败，或界面明确显示还没签到）。 */
    public static final int FAIL = 2;
    /** 拉起来了，但界面判断不了。 */
    public static final int UNKNOWN = 0;

    /**
     * @param source 触发来源，写进历史：闹钟 / 解锁 / 测试
     * @return {@link #OK} / {@link #FAIL} / {@link #UNKNOWN}
     */
    public static int execute(Context c, Routine r, String source) {
        long now = System.currentTimeMillis();
        boolean launched = Launcher.run(c, r);
        int result;
        String note;

        if (!launched) {
            result = FAIL;
            note = "拉起快捷方式失败";
        } else if (r.type == Routine.TYPE_SHORTCUT) {
            int v = Verifier.checkOnce(c);
            if (v == FAIL) {
                Logx.i(c, "校验显示未签到，重试一次");
                sleep(1200);
                Launcher.run(c, r);
                v = Verifier.checkOnce(c);
                note = v == OK ? "重试后确认已签到" : v == FAIL ? "重试后仍未签到" : "重试后无法校验";
            } else {
                note = v == OK ? "界面确认已签到" : "已拉起，界面无法校验";
            }
            result = v == FAIL ? FAIL : OK;
        } else {
            result = OK;
            note = "已打开应用";
        }

        r.lastRunAt = now;
        r.lastResult = result == FAIL ? 2 : 1;
        RoutineStore.upsert(c, r);
        History.add(c, r, source, now, result, note);
        Logx.i(c, "执行结果=" + label(result) + "（" + note + "，来源 " + source + "）");
        return result;
    }

    public static String label(int result) {
        return result == OK ? "成功" : result == FAIL ? "失败" : "未确认";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
