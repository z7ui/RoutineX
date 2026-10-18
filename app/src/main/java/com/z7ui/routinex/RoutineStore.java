package com.z7ui.routinex;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 任务存储在 SharedPreferences 里，JSON 数组，不需要数据库。 */
public class RoutineStore {

    private static final String PREF = "routinex";
    private static final String KEY_ITEMS = "items";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Routine> load(Context c) {
        List<Routine> list = new ArrayList<>();
        String raw = sp(c).getString(KEY_ITEMS, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                list.add(Routine.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void save(Context c, List<Routine> list) {
        JSONArray arr = new JSONArray();
        for (Routine r : list) {
            try {
                arr.put(r.toJson());
            } catch (Exception ignored) {
            }
        }
        sp(c).edit().putString(KEY_ITEMS, arr.toString()).apply();
    }

    public static Routine find(Context c, String id) {
        if (id == null) return null;
        for (Routine r : load(c)) {
            if (id.equals(r.id)) return r;
        }
        return null;
    }

    public static void upsert(Context c, Routine r) {
        List<Routine> list = load(c);
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(r.id)) {
                list.set(i, r);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(r);
        save(c, list);
    }

    public static void remove(Context c, String id) {
        List<Routine> list = load(c);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(id)) {
                list.remove(i);
                break;
            }
        }
        save(c, list);
    }
}
