package com.z7ui.routinex;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class Notifier {

    public static final String CH_PENDING = "pending";
    public static final int ID_PENDING = 1001;

    public static void toast(Context c, String msg) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(c.getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CH_PENDING) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CH_PENDING, "等待解锁执行", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("任务到点时手机处于锁屏，解锁后立刻执行");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    public static Notification pending(Context c, String text) {
        ensureChannel(c);
        return new NotificationCompat.Builder(c, CH_PENDING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle("等解锁后执行")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }
}
