package com.z7ui.routinex;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 负责把任务排进 AlarmManager。 */
public class Scheduler {

    public static void scheduleAll(Context c) {
        for (Routine r : RoutineStore.load(c)) {
            if (r.enabled) schedule(c, r);
            else cancel(c, r);
        }
    }

    public static void schedule(Context c, Routine r) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long at = r.nextTriggerMillis(System.currentTimeMillis());
        PendingIntent pi = pi(c, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context c, Routine r) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pi(c, r, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static PendingIntent pi(Context c, Routine r, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("rid", r.id);
        return PendingIntent.getBroadcast(c, r.id.hashCode(), i, flags);
    }
}
