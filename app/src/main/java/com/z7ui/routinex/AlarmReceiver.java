package com.z7ui.routinex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 闹钟到点。先排下一次，再决定：
 * 手机锁着 -> 交给 PendingService 等解锁；否则立刻执行。
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String rid = intent.getStringExtra("rid");
        Routine r = RoutineStore.find(context, rid);
        Logx.i(context, "闹钟触发 rid=" + rid + " 任务存在=" + (r != null));
        if (r == null || !r.enabled) return;

        Scheduler.schedule(context, r);   // 先把下一次排好，避免中途出错丢掉

        boolean locked = Util.isLocked(context);
        Logx.i(context, "locked=" + locked + " defer=" + r.deferUntilUnlock);

        if (locked && r.deferUntilUnlock) {
            PendingService.start(context, r.id);
        } else {
            boolean ok = Launcher.run(context, r);
            Logx.i(context, "立即执行结果=" + ok);
            r.lastRunAt = System.currentTimeMillis();
            r.lastResult = ok ? 1 : 2;
            RoutineStore.upsert(context, r);
            Notifier.toast(context, ok ? "已执行：" + r.title : "执行失败：" + r.title);
        }
    }
}
