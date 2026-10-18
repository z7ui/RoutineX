package com.z7ui.routinex;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.PowerManager;
import android.util.TypedValue;

public class Util {

    /** 锁屏或息屏都算「现在跑不了」。 */
    public static boolean isLocked(Context c) {
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        boolean keyguard = km != null && km.isKeyguardLocked();
        boolean screenOff = pm != null && !pm.isInteractive();
        return keyguard || screenOff;
    }

    public static boolean canScheduleExactAlarms(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
