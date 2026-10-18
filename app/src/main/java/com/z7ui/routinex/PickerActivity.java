package com.z7ui.routinex;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 通用选择器：选应用，或选某个应用的快捷方式。 */
public class PickerActivity extends AppCompatActivity {

    public static final int MODE_APP = 0;
    public static final int MODE_SHORTCUT = 1;

    private static class Item {
        String label, sub, pkg, uri, kind;

        Item(String label, String sub, String pkg, String uri, String kind) {
            this.label = label;
            this.sub = sub;
            this.pkg = pkg;
            this.uri = uri;
            this.kind = kind;
        }
    }

    private final List<Item> data = new ArrayList<>();
    private ListView listView;
    private ProgressBar bar;
    private TextView empty;
    private Adapter adapter;
    private int mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);

        mode = getIntent().getIntExtra("mode", MODE_APP);
        String pkg = getIntent().getStringExtra("pkg");
        String appLabel = getIntent().getStringExtra("label");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        // 用 Activity 的标题，否则会被 manifest 里的应用名覆盖掉
        setTitle(mode == MODE_APP ? "选择应用" : "选择快捷方式");
        toolbar.setSubtitle(mode == MODE_APP ? null : appLabel);
        toolbar.setNavigationOnClickListener(v -> finish());

        listView = findViewById(R.id.list);
        bar = findViewById(R.id.bar);
        empty = findViewById(R.id.empty);

        adapter = new Adapter();
        listView.setAdapter(adapter);

        if (mode == MODE_APP) loadApps();
        else loadShortcuts(pkg);
    }

    private void loadApps() {
        bar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Item> items = new ArrayList<>();
            PackageManager pm = getPackageManager();
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
            Map<String, Item> uniq = new LinkedHashMap<>();
            for (ResolveInfo ri : ris) {
                String p = ri.activityInfo.packageName;
                if (p.equals(getPackageName())) continue;
                if (uniq.containsKey(p)) continue;
                CharSequence lb = ri.loadLabel(pm);
                uniq.put(p, new Item(lb.toString(), p, p, "", "app"));
            }
            items.addAll(uniq.values());
            final Collator coll = Collator.getInstance(Locale.CHINA);
            items.sort(Comparator.comparing(a -> a.label, coll));
            runOnUiThread(() -> {
                bar.setVisibility(View.GONE);
                data.clear();
                data.addAll(items);
                adapter.notifyDataSetChanged();
                empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                empty.setText("没有找到可启动的应用");
            });
        }).start();
    }

    private void loadShortcuts(String pkg) {
        bar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            List<Item> items = new ArrayList<>();
            items.add(new Item("直接打开应用", "不执行快捷方式，只把 App 打开", pkg, "", "app"));

            boolean root = ShortcutScanner.hasRoot();
            if (root) {
                for (ShortcutScanner.Shot s : ShortcutScanner.scan(pkg)) {
                    items.add(new Item(s.label, s.path, pkg, s.uri, "shot"));
                }
            }
            final boolean hasRoot = root;
            runOnUiThread(() -> {
                bar.setVisibility(View.GONE);
                data.clear();
                data.addAll(items);
                adapter.notifyDataSetChanged();
                if (items.size() <= 1) {
                    empty.setVisibility(View.VISIBLE);
                    empty.setText(hasRoot
                            ? "这个应用没有可读取的快捷方式\n（只有动态快捷方式能被读出来）"
                            : "读取快捷方式需要 root\n请授权后重试");
                } else {
                    empty.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView != null ? convertView
                    : getLayoutInflater().inflate(R.layout.item_picker, parent, false);
            TextView t = v.findViewById(R.id.tvLabel);
            TextView s = v.findViewById(R.id.tvSub);
            final Item it = data.get(position);
            t.setText(it.label);
            s.setText(it.sub);
            s.setVisibility(it.sub == null || it.sub.isEmpty() ? View.GONE : View.VISIBLE);
            final int pos = position;
            v.setOnClickListener(view -> select(pos));
            return v;
        }
    }

    private void select(int position) {
        if (position < 0 || position >= data.size()) return;
        Item it = data.get(position);
        Intent out = new Intent();
        out.putExtra("label", it.label);
        out.putExtra("pkg", it.pkg);
        out.putExtra("uri", it.uri);
        out.putExtra("kind", it.kind);
        setResult(RESULT_OK, out);
        finish();
    }
}
