package com.z7ui.routinex;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private LinearLayout listContainer;
    private TextView emptyTip;
    private TextView permText;
    private MaterialCardView permCard;
    private MaterialSwitch swLog;
    private TextView tvLogSub;
    private TextView tvLogSize;
    private TextView tvAboutSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        listContainer = findViewById(R.id.listContainer);
        emptyTip = findViewById(R.id.emptyTip);
        permText = findViewById(R.id.permText);
        permCard = findViewById(R.id.permCard);
        swLog = findViewById(R.id.swLog);
        tvLogSub = findViewById(R.id.tvLogSub);
        tvLogSize = findViewById(R.id.tvLogSize);
        tvAboutSub = findViewById(R.id.tvAboutSub);

        setupSettings();

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, EditActivity.class)));

        // 每次进来重排一遍，避免系统清理或改时间后闹钟丢失
        Scheduler.scheduleAll(this);

        // 通知权限（Android 13+）：前台服务要靠它显示"等待解锁"的通知
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 10);
            }
        }

        findViewById(R.id.btnExact).setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        findViewById(R.id.btnBattery).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            try {
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        findViewById(R.id.btnNotif).setOnClickListener(v -> {
            Intent i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            try {
                startActivity(i);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderList();
        renderPerm();
        refreshLogSize();
    }

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

    private void renderPerm() {
        boolean exact = Util.canScheduleExactAlarms(this);
        boolean lazy = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M
                || ((android.os.PowerManager) getSystemService(POWER_SERVICE))
                .isIgnoringBatteryOptimizations(getPackageName());

        if (exact && lazy) {
            permCard.setVisibility(View.GONE);
            return;
        }
        permCard.setVisibility(View.VISIBLE);
        StringBuilder sb = new StringBuilder();
        if (!exact) sb.append("· 精确闹钟：未授权，定时可能延后\n");
        if (!lazy) sb.append("· 电池优化：未加白名单，后台可能被清掉\n");
        sb.append("· 小米还需要在「应用设置 → 自启动」里允许本应用");
        permText.setText(sb.toString().trim());
    }

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
