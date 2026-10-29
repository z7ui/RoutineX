package com.z7ui.routinex;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView emptyTip;
    private MaterialCardView permCard;
    private MaterialSwitch swLog;
    private TextView tvLogSub;
    private TextView tvLogSize;
    private TextView tvHistorySub;
    private TextView tvAboutSub;
    private TextView tvRootState;
    private TextView tvNotifState;
    private TextView tvExactState;
    private TextView tvBatteryState;
    private TextView tvAutoState;

    /** root 检查会弹 root 管理器的授权框，一个进程只自动查一次。 */
    private static Boolean rootCached;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listContainer = findViewById(R.id.listContainer);
        emptyTip = findViewById(R.id.emptyTip);
        permCard = findViewById(R.id.permCard);
        swLog = findViewById(R.id.swLog);
        tvLogSub = findViewById(R.id.tvLogSub);
        tvLogSize = findViewById(R.id.tvLogSize);
        tvAboutSub = findViewById(R.id.tvAboutSub);

        tvRootState = findViewById(R.id.tvRootState);
        tvNotifState = findViewById(R.id.tvNotifState);
        tvExactState = findViewById(R.id.tvExactState);
        tvBatteryState = findViewById(R.id.tvBatteryState);
        tvAutoState = findViewById(R.id.tvAutoState);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, EditActivity.class)));

        // 每次进来重排一遍，避免系统清理或改时间后闹钟丢失
        Scheduler.scheduleAll(this);

        setupPerms();
        setupSettings();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
        renderPerms();
        refreshLogSize();
        // 从编辑页点完「测试执行一次」回来时，历史已经多了一条，
        // 但 Activity 没有重建——只在 onCreate 里刷会一直显示旧值。
        refreshHistorySub();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        renderPerms();
    }

    // ---------------- 权限 ----------------

    private void setupPerms() {
        findViewById(R.id.rowRoot).setOnClickListener(v -> askRoot());
        findViewById(R.id.rowNotifPerm).setOnClickListener(v -> openNotif());
        findViewById(R.id.rowExact).setOnClickListener(v -> openExactAlarm());
        findViewById(R.id.rowBattery).setOnClickListener(v -> openBattery());
        findViewById(R.id.rowAutoStart).setOnClickListener(v -> openAutoStart());
    }

    private void renderPerms() {
        setPerm(tvNotifState, notifGranted());
        setPerm(tvExactState, Util.canScheduleExactAlarms(this));
        setPerm(tvBatteryState, ignoringBattery());

        if (rootCached == null) {
            if (Util.isLocked(this)) {
                // 锁屏时 root 管理器弹不出授权框，静默查只会拿到失败结果
                tvRootState.setText("点击申请");
                tvRootState.setTextColor(gray());
                return;
            }
            tvRootState.setText("检查中");
            tvRootState.setTextColor(gray());
            new Thread(() -> {
                boolean ok = ShortcutScanner.hasRoot();
                rootCached = ok;
                runOnUiThread(() -> setPerm(tvRootState, ok));
            }).start();
        } else {
            setPerm(tvRootState, rootCached);
        }
    }

    private void setPerm(TextView v, boolean ok) {
        v.setText(ok ? "已授权" : "未授权");
        v.setTextColor(ok ? ContextCompat.getColor(this, R.color.perm_ok)
                : ContextCompat.getColor(this, R.color.perm_missing));
    }

    @ColorInt
    private int gray() {
        return ContextCompat.getColor(this, R.color.perm_unknown);
    }

    private boolean notifGranted() {
        if (Build.VERSION.SDK_INT < 33) return true;
        return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ignoringBattery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void askRoot() {
        if (Boolean.TRUE.equals(rootCached)) {
            Toast.makeText(this, "已经授权了", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Util.isLocked(this)) {
            Toast.makeText(this, "root 授权框在锁屏时弹不出来，解锁后再点", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "正在请求 root，注意看 root 管理器的弹窗", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            boolean ok = ShortcutScanner.hasRoot();
            rootCached = ok;
            runOnUiThread(() -> {
                setPerm(tvRootState, ok);
                Toast.makeText(this, ok ? "root 已授权" : "没有拿到 root", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void openNotif() {
        if (notifGranted()) {
            Toast.makeText(this, "通知已经开着了", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
            return;
        }
        Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startSafely(i);
    }

    private void openExactAlarm() {
        if (Util.canScheduleExactAlarms(this)) {
            Toast.makeText(this, "精确闹钟已经开着了", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startSafely(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } else {
            Toast.makeText(this, "这个系统版本不需要单独授权", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBattery() {
        if (ignoringBattery()) {
            Toast.makeText(this, "已经是无限制了", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent direct = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:" + getPackageName()));
        if (!startSafely(direct)) {
            startSafely(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void openAutoStart() {
        // 小米的自启动管理页没有公开入口，只能硬指过去；指不到就退到应用详情
        Intent mi = new Intent();
        mi.setComponent(new ComponentName("com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"));
        if (startSafely(mi)) return;

        Intent oppo = new Intent();
        oppo.setComponent(new ComponentName("com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
        if (startSafely(oppo)) return;

        startSafely(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    private boolean startSafely(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- 设置 ----------------

    private void setupSettings() {
        boolean on = Prefs.logEnabled(this);
        swLog.setChecked(on);
        tvLogSub.setText(on ? "已打开，会写入 files/rx.log" : "默认关闭，打开后记录每一步执行");
        swLog.setOnCheckedChangeListener((b, checked) -> {
            Prefs.setLogEnabled(this, checked);
            tvLogSub.setText(checked ? "已打开，会写入 files/rx.log" : "默认关闭，打开后记录每一步执行");
            refreshLogSize();
        });
        findViewById(R.id.rowLog).setOnClickListener(v -> swLog.toggle());
        tvHistorySub = findViewById(R.id.tvHistorySub);
        findViewById(R.id.rowHistory).setOnClickListener(v -> showHistory());
        findViewById(R.id.rowHistory).setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("清空执行历史？")
                    .setMessage("只删历史记录，不影响任务和日志。")
                    .setPositiveButton("清空", (d, w) -> {
                        History.clear(this);
                        refreshHistorySub();
                        Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });
        refreshHistorySub();
        findViewById(R.id.rowClearLog).setOnClickListener(v -> clearLog());
        findViewById(R.id.rowAbout).setOnClickListener(v -> showAbout());
        tvAboutSub.setText(getString(R.string.about_subtitle, versionName()));
    }

    private void refreshLogSize() {
        tvLogSize.setText(Logx.sizeText(this));
    }

    private void clearLog() {
        long n = Logx.size(this);
        if (n <= 0) {
            Toast.makeText(this, "没有日志可清除", Toast.LENGTH_SHORT).show();
            refreshLogSize();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清除日志")
                .setMessage("共 " + Logx.sizeText(this) + "，确定删除？")
                .setPositiveButton("删除", (d, w) -> {
                    long freed = Logx.clear(this);
                    Toast.makeText(this, "已清除 " + (freed / 1024) + " KB", Toast.LENGTH_SHORT).show();
                    refreshLogSize();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshHistorySub() {
        if (tvHistorySub == null) return;
        List<String> lines = History.recentLines(this, 1);
        // 历史条目是「首行 + 缩进的备注行」，压成一行时要把缩进一起去掉，
        // 否则会显示成「每日签到 ·     界面确认已签到」这种中间一坨空格。
        tvHistorySub.setText(lines.isEmpty()
                ? "还没有记录，执行一次后会出现"
                : "最近一次：" + lines.get(0).replaceAll("\\s*\\n\\s*", " · "));
    }

    private void showHistory() {
        List<String> lines = History.recentLines(this, 30);
        TextView tv = new TextView(this);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad, pad, pad);
        tv.setTextSize(13f);
        tv.setLineSpacing(0f, 1.25f);
        tv.setText(lines.isEmpty() ? "还没有记录。\n\n执行一次任务后，这里会有「时间 / 结果 / 来源 / 备注」。" : String.join("\n\n", lines));

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("执行历史（最近 30 条）")
                .setView(sv)
                .setPositiveButton("关闭", null);
        if (!lines.isEmpty()) {
            b.setNeutralButton("清空", (d, w) -> {
                History.clear(this);
                refreshHistorySub();
            });
        }
        b.show();
    }

    private void showAbout() {
        String body = getString(R.string.about_body) + "\n\n"
                + getString(R.string.about_version, versionName());
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(body)
                .setPositiveButton(R.string.about_ok, null)
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    // ---------------- 列表 ----------------

    private void renderList() {
        listContainer.removeAllViews();
        List<Routine> list = RoutineStore.load(this);
        emptyTip.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Routine r : list) {
            View item = inflater.inflate(R.layout.item_routine, listContainer, false);
            TextView time = item.findViewById(R.id.tvTime);
            TextView title = item.findViewById(R.id.tvTitle);
            TextView summary = item.findViewById(R.id.tvSummary);
            MaterialSwitch sw = item.findViewById(R.id.swEnabled);

            time.setText(r.timeText());
            title.setText(r.title == null || r.title.isEmpty() ? r.shortcutLabel : r.title);
            summary.setText(r.summary());
            sw.setChecked(r.enabled);

            sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                r.enabled = isChecked;
                RoutineStore.upsert(this, r);
                if (isChecked) Scheduler.schedule(this, r);
                else Scheduler.cancel(this, r);
            });

            item.setOnClickListener(v -> {
                Intent i = new Intent(this, EditActivity.class);
                i.putExtra("rid", r.id);
                startActivity(i);
            });
            listContainer.addView(item);
        }
    }
}
