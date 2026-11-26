package com.z7ui.routinex;

import android.content.Context;
import android.content.SharedPreferences;

/** 少量全局设置。目前只有「运行日志」开关，默认关闭。 */
public class Prefs {

    private static final String FILE = "settings";
    private static final String KEY_LOG = "log_enabled";

    private static volatile Boolean cachedLog;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean logEnabled(Context c) {
        Boolean v = cachedLog;
        if (v == null) {
            v = sp(c).getBoolean(KEY_LOG, false);
            cachedLog = v;
        }
        return v;
    }

    public static void setLogEnabled(Context c, boolean on) {
        cachedLog = on;
        sp(c).edit().putBoolean(KEY_LOG, on).apply();
    }

    /** 导入备份后调用：清掉内存缓存，否则界面还显示旧值 */
    public static void invalidate() {
        cachedLog = null;
    }
}
