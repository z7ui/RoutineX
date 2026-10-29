package com.z7ui.routinex;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

public class EditActivity extends AppCompatActivity {

    private static final int REQ_APP = 1;
    private static final int REQ_SHOT = 2;

    private Routine r;
    private boolean isNew;

    private TextView tvTime, tvDays, tvApp, tvShot, tvTitle;
    private MaterialSwitch swDefer;
    private Button btnDelete;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        String rid = getIntent().getStringExtra("rid");
        if (rid != null) {
            r = RoutineStore.find(this, rid);
        }
        isNew = (r == null);
        if (r == null) r = new Routine();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(isNew ? "新建任务" : "编辑任务");
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTime = findViewById(R.id.tvTime);
        tvDays = findViewById(R.id.tvDays);
        tvApp = findViewById(R.id.tvApp);
        tvShot = findViewById(R.id.tvShot);
        tvTitle = findViewById(R.id.tvTitle);
        swDefer = findViewById(R.id.swDefer);
        btnDelete = findViewById(R.id.btnDelete);

        findViewById(R.id.rowTitle).setOnClickListener(v -> editTitle());
        findViewById(R.id.rowTime).setOnClickListener(v -> pickTime());
        findViewById(R.id.rowDays).setOnClickListener(v -> pickDays());
        findViewById(R.id.rowApp).setOnClickListener(v -> pickApp());
        findViewById(R.id.rowShot).setOnClickListener(v -> pickShot());
        findViewById(R.id.rowTest).setOnClickListener(v -> testRun());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());

        btnDelete.setVisibility(isNew ? View.GONE : View.VISIBLE);
        btnDelete.setOnClickListener(v -> confirmDelete());

        swDefer.setChecked(r.deferUntilUnlock);
        swDefer.setOnCheckedChangeListener((b, checked) -> r.deferUntilUnlock = checked);

        refresh();
    }

    private void refresh() {
        tvTime.setText(r.timeText());
        tvDays.setText(r.daysText());
        tvApp.setText(r.targetLabel == null || r.targetLabel.isEmpty() ? "未选择" : r.targetLabel);
        if (r.type == Routine.TYPE_APP) {
            tvShot.setText("直接打开应用");
        } else {
            tvShot.setText(r.shortcutLabel == null || r.shortcutLabel.isEmpty()
                    ? (r.uri.isEmpty() ? "未选择" : "已设置") : r.shortcutLabel);
        }
        String t = r.title == null || r.title.isEmpty() ? "" : r.title;
        tvTitle.setText(t.isEmpty() ? "跟随快捷方式名" : t);
    }

    private void pickTime() {
        new TimePickerDialog(this, (view, h, m) -> {
            r.hour = h;
            r.minute = m;
            refresh();
        }, r.hour, r.minute, true).show();
    }

    private void pickDays() {
        final String[] names = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        final boolean[] checked = new boolean[7];
        for (int i = 0; i < 7; i++) checked[i] = ((r.daysMask >> i) & 1) == 1;

        new AlertDialog.Builder(this)
                .setTitle("重复")
                .setMultiChoiceItems(names, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setNeutralButton("工作日", (d, w) -> {
                    r.daysMask = Routine.WEEKDAYS;
                    refresh();
                })
                .setNegativeButton("每天", (d, w) -> {
                    r.daysMask = Routine.EVERY_DAY;
                    refresh();
                })
                .setPositiveButton("确定", (d, w) -> {
                    int mask = 0;
                    for (int i = 0; i < 7; i++) if (checked[i]) mask |= (1 << i);
                    if (mask != 0) r.daysMask = mask;
                    refresh();
                })
                .show();
    }

    private void pickApp() {
        Intent i = new Intent(this, PickerActivity.class);
        i.putExtra("mode", PickerActivity.MODE_APP);
        startActivityForResult(i, REQ_APP);
    }

    private void pickShot() {
        if (r.targetPackage == null || r.targetPackage.isEmpty()) {
            Toast.makeText(this, "先选目标应用", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, PickerActivity.class);
        i.putExtra("mode", PickerActivity.MODE_SHORTCUT);
        i.putExtra("pkg", r.targetPackage);
        i.putExtra("label", r.targetLabel);
        startActivityForResult(i, REQ_SHOT);
    }

    private void editTitle() {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setText(r.title == null ? "" : r.title);
        et.setHint("留空则使用快捷方式名");
        new AlertDialog.Builder(this)
                .setTitle("任务名称")
                .setView(et)
                .setPositiveButton("确定", (d, w) -> {
                    r.title = et.getText().toString().trim();
                    refresh();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void testRun() {
        if (r.targetPackage == null || r.targetPackage.isEmpty()) {
            Toast.makeText(this, "先选目标应用", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "已触发，正在校验结果…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int result = Runner.execute(getApplicationContext(), r, "测试");
            runOnUiThread(() -> Toast.makeText(this,
                    result == Runner.OK ? "执行成功（" + (r.lastResult == 1 ? "已记账" : "") + "）"
                            : result == Runner.FAIL ? "执行失败，看下界面" : "已执行，但判断不了结果",
                    Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void save() {
        if (r.targetPackage == null || r.targetPackage.isEmpty()) {
            Toast.makeText(this, "请先选择目标应用", Toast.LENGTH_SHORT).show();
            return;
        }
        if (r.type == Routine.TYPE_SHORTCUT && (r.uri == null || r.uri.isEmpty())) {
            Toast.makeText(this, "请选择要执行的快捷方式", Toast.LENGTH_SHORT).show();
            return;
        }
        if (r.title == null || r.title.isEmpty()) {
            r.title = (r.shortcutLabel != null && !r.shortcutLabel.isEmpty())
                    ? r.shortcutLabel : r.targetLabel;
        }
        RoutineStore.upsert(this, r);
        Scheduler.schedule(this, r);
        Toast.makeText(this, "已保存：" + r.timeText() + " " + r.daysText(), Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除任务")
                .setMessage("确定删除「" + (r.title == null ? "" : r.title) + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    Scheduler.cancel(this, r);
                    RoutineStore.remove(this, r.id);
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == REQ_APP) {
            String pkg = data.getStringExtra("pkg");
            String label = data.getStringExtra("label");
            if (pkg != null) {
                if (!pkg.equals(r.targetPackage)) {
                    r.shortcutLabel = "";
                    r.uri = "";
                    r.type = Routine.TYPE_SHORTCUT;
                }
                r.targetPackage = pkg;
                r.targetLabel = label;
            }
            refresh();
            // 选完应用直接进快捷方式选择，少点一次
            if (pkg != null) pickShot();
        } else if (requestCode == REQ_SHOT) {
            String kind = data.getStringExtra("kind");
            if ("app".equals(kind)) {
                r.type = Routine.TYPE_APP;
                r.shortcutLabel = "";
                r.uri = "";
            } else {
                r.type = Routine.TYPE_SHORTCUT;
                r.uri = data.getStringExtra("uri");
                r.shortcutLabel = data.getStringExtra("label");
            }
            refresh();
        }
    }
}
