package com.z7ui.routinex;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 应用更新后重新排闹钟（否则重启一次所有任务就没了）。 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.scheduleAll(context);
    }
}
