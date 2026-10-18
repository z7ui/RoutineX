package com.z7ui.routinex;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.UUID;

/**
 * 一条定时任务：每天（或指定的星期）在某个时刻，执行某个 App 的某个快捷方式。
 */
public class Routine {

    public static final int TYPE_SHORTCUT = 0;  // 执行长按菜单里的快捷方式
    public static final int TYPE_APP = 1;       // 直接打开应用

    public static final int EVERY_DAY = 0b1111111;   // 周一..周日
    public static final int WEEKDAYS  = 0b0011111;   // 周一..周五
    public static final int WEEKEND   = 0b1100000;   // 周六 + 周日

    public String id = UUID.randomUUID().toString();
    public String title = "";
    public int hour = 9;
    public int minute = 0;
    /** bit0 = 周一, bit6 = 周日 */
    public int daysMask = EVERY_DAY;
    public String targetPackage = "";
    public String targetLabel = "";
    public String shortcutLabel = "";
    public String uri = "";
    public int type = TYPE_SHORTCUT;
    /** 到点时手机处于锁屏/息屏状态，是否等到下次解锁再执行 */
    public boolean deferUntilUnlock = true;
    public boolean enabled = true;
    public long lastRunAt = 0L;
    /** 0 = 还没跑过，1 = 上次成功，2 = 上次失败 */
    public int lastResult = 0;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("hour", hour);
        o.put("minute", minute);
        o.put("daysMask", daysMask);
        o.put("targetPackage", targetPackage);
        o.put("targetLabel", targetLabel);
        o.put("shortcutLabel", shortcutLabel);
        o.put("uri", uri);
        o.put("type", type);
        o.put("deferUntilUnlock", deferUntilUnlock);
        o.put("enabled", enabled);
        o.put("lastRunAt", lastRunAt);
        o.put("lastResult", lastResult);
        return o;
    }

    public static Routine fromJson(JSONObject o) {
        Routine r = new Routine();
        r.id = o.optString("id", r.id);
        r.title = o.optString("title", "");
        r.hour = o.optInt("hour", 9);
        r.minute = o.optInt("minute", 0);
        r.daysMask = o.optInt("daysMask", EVERY_DAY);
        r.targetPackage = o.optString("targetPackage", "");
        r.targetLabel = o.optString("targetLabel", "");
        r.shortcutLabel = o.optString("shortcutLabel", "");
        r.uri = o.optString("uri", "");
        r.type = o.optInt("type", TYPE_SHORTCUT);
        r.deferUntilUnlock = o.optBoolean("deferUntilUnlock", true);
        r.enabled = o.optBoolean("enabled", true);
        r.lastRunAt = o.optLong("lastRunAt", 0L);
        r.lastResult = o.optInt("lastResult", 0);
        return r;
    }

    /** 以 now 为基准，算出下一次应该触发的时刻（毫秒时间戳）。 */
    public long nextTriggerMillis(long now) {
        for (int i = 0; i <= 8; i++) {
            Calendar t = Calendar.getInstance();
            t.setTimeInMillis(now);
            t.add(Calendar.DAY_OF_YEAR, i);
            t.set(Calendar.HOUR_OF_DAY, hour);
            t.set(Calendar.MINUTE, minute);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);

            int idx = (t.get(Calendar.DAY_OF_WEEK) + 5) % 7;  // 周一 = 0
            if (((daysMask >> idx) & 1) == 0) continue;

            long ms = t.getTimeInMillis();
            if (ms > now + 1000L) return ms;
        }
        return now + 24L * 3600L * 1000L;
    }

    public String timeText() {
        return String.format(java.util.Locale.US, "%02d:%02d", hour, minute);
    }

    public String daysText() {
        if (daysMask == EVERY_DAY) return "每天";
        if (daysMask == WEEKDAYS) return "工作日";
        if (daysMask == WEEKEND) return "周末";
        if (daysMask == 0) return "未选择";
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        StringBuilder sb = new StringBuilder("每周 ");
        for (int i = 0; i < 7; i++) {
            if (((daysMask >> i) & 1) == 1) sb.append(names[i]);
        }
        return sb.toString();
    }

    /** 列表里第二行显示的内容 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(daysText()).append(" · ");
        if (targetLabel != null && !targetLabel.isEmpty()) sb.append(targetLabel);
        if (type == TYPE_SHORTCUT && shortcutLabel != null && !shortcutLabel.isEmpty()) {
            sb.append(" · ").append(shortcutLabel);
        } else if (type == TYPE_APP) {
            sb.append(" · 直接打开");
        }
        return sb.toString();
    }
}
