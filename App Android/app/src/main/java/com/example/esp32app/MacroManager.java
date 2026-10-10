package com.example.esp32app;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.esp32app.data.MacroSerializer;

import java.util.List;

public class MacroManager {
    private static final String PREF_NAME = "MacroPrefs";
    private static final String KEY_MACROS = "saved_macros";

    public static void saveMacros(Context context, List<Macro> macros) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = MacroSerializer.serialize(macros);
        prefs.edit().putString(KEY_MACROS, json).apply();
    }

    public static List<Macro> getMacros(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_MACROS, "[]");
        return MacroSerializer.deserialize(json);
    }
}
