package com.example.esp32app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MacroManager {
    private static final String PREF_NAME = "MacroPrefs";
    private static final String KEY_MACROS = "saved_macros";

    public static void saveMacros(Context context, List<Macro> macros) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        JSONArray jsonArray = new JSONArray();
        try {
            for (Macro m : macros) {
                JSONObject obj = new JSONObject();
                obj.put("name", m.getName());
                obj.put("content", m.getContent());
                jsonArray.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        prefs.edit().putString(KEY_MACROS, jsonArray.toString()).apply();
    }

    public static List<Macro> getMacros(Context context) {
        List<Macro> macros = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_MACROS, "[]");
        try {
            JSONArray jsonArray = new JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                macros.add(new Macro(obj.optString("name", "Unnamed"), obj.optString("content", "")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return macros;
    }
}
