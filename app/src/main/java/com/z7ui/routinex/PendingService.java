package com.z7ui.routinex;

import android.app.KeyguardManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

/**
 * 到点时手机锁着，就由这个前台服务挂起等待，解锁后立刻执行。
 *
 * 为什么不是只监听 ACTION_USER_PRESENT：
 * MIUI 上闹钟可能被压到解锁那一瞬间才投递，服务刚起来时解锁广播已经发完了，
 * 只靠广播会永远等不到。所以改成「广播当快路径 + 500ms 轮询兜底」。
 */
public class PendingService extends Service {

    private static final String ACTION_START = "com.z7ui.routinex.PENDING_START";
    private static final String EXTRA_RID = "rid";
    private static final long TIMEOUT_MS = 8L * 3600L * 1000L;   // 8 小时没解锁就放弃
    private static final long POLL_MS = 500L;
    /** 检测到解锁后再等一会儿，让系统把解锁动画和窗口切换走完。 */
    private static final long UNLOCK_SETTLE_MS = 400L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BroadcastReceiver unlockRx;
    private String rid;
    private boolean done;
    private boolean settling;

    public static void start(Context c, String rid) {
        Intent i = new Intent(c, PendingService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_RID, rid);
        ContextCompat.startForegroundService(c, i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        rid = intent != null ? intent.getStringExtra(EXTRA_RID) : null;
        Routine r = RoutineStore.find(this, rid);
        Logx.i(this, "服务启动 rid=" + rid + " 找到任务=" + (r != null));
        if (r == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String title = r.title == null || r.title.isEmpty() ? r.shortcutLabel : r.title;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, Notifier.ID_PENDING,
                        Notifier.pending(this, title), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                ServiceCompat.startForeground(this, Notifier.ID_PENDING,
                        Notifier.pending(this, title), 0);
            }
            Logx.i(this, "前台服务已挂起，等待解锁");
        } catch (Exception e) {
            Logx.i(this, "startForeground 失败: " + e);
        }

        if (unlockRx == null) {
            unlockRx = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Logx.i(PendingService.this, "收到解锁广播");
                    scheduleRun("broadcast");
                }
            };
            ContextCompat.registerReceiver(this, unlockRx,
                    new IntentFilter(Intent.ACTION_USER_PRESENT),
                    ContextCompat.RECEIVER_NOT_EXPORTED);
        }

        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::stopSelf, TIMEOUT_MS);

        // 轮询兜底：解不解锁由状态判断，不依赖广播时机
        Logx.i(this, "开始轮询锁屏状态，当前 isLocked=" + Util.isLocked(this));
        handler.post(poll);
        return START_STICKY;
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (done) return;
            if (!Util.isLocked(PendingService.this)) {
                Logx.i(PendingService.this, "轮询发现已解锁");
                scheduleRun("poll");
                return;
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    /** 解锁后等一小会儿再执行，避免刚解锁就抢前台。 */
    private void scheduleRun(String from) {
        if (done || settling) return;
        settling = true;
        handler.removeCallbacks(poll);
        Logx.i(this, "解锁确认，等 " + UNLOCK_SETTLE_MS + "ms 后执行（来自 " + from + "）");
        handler.postDelayed(() -> runNow(from), UNLOCK_SETTLE_MS);
    }

    private void runNow(String from) {
        if (done) return;
        done = true;
        handler.removeCallbacks(poll);
        Routine r = RoutineStore.find(this, rid);
        if (r != null) {
            Logx.i(this, "执行（来自 " + from + "）:" + r.title);
            int result = Runner.execute(this, r, "解锁");
            Notifier.toast(this, result == Runner.FAIL
                    ? "执行失败：" + r.title : "解锁后已执行：" + r.title);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        done = true;
        handler.removeCallbacksAndMessages(null);
        if (unlockRx != null) {
            try {
                unregisterReceiver(unlockRx);
            } catch (Exception ignored) {
            }
            unlockRx = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** 解锁监听在部分机型上可能需要 KeyguardManager 的锁屏状态缓存刷新。 */
    @SuppressWarnings("unused")
    private boolean unlocked() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return km != null && !km.isKeyguardLocked();
    }
}
