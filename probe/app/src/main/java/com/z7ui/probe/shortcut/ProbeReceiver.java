package com.z7ui.probe.shortcut;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 用 adb 触发：am broadcast -n com.z7ui.probe.shortcut/.ProbeReceiver --es mode launch ... */
public class ProbeReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = intent.getStringExtra("mode");
        String pkg = intent.getStringExtra("pkg");
        String id = intent.getStringExtra("id");
        if (mode == null) mode = "enum";
        if (pkg == null) pkg = "com.byd.aeri.caranywhere";

        String result = Probe.run(context, mode, pkg, id);
        setResultData(result);
    }
}
