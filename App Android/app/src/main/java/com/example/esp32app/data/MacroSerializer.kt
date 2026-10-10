package com.example.esp32app.data

import com.example.esp32app.Macro
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object MacroSerializer {

    @JvmStatic
    fun serialize(macros: List<Macro>): String {
        val jsonArray = JSONArray()
        for (m in macros) {
            val obj = JSONObject()
            obj.put("name", m.name)
            obj.put("content", m.content)
            jsonArray.put(obj)
        }
        return jsonArray.toString()
    }

    @JvmStatic
    fun deserialize(json: String?): List<Macro> {
        val list = mutableListOf<Macro>()
        if (json.isNullOrBlank()) {
            return list
        }
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val name = obj.optString("name", "Unnamed")
                val content = obj.optString("content", "")
                list.add(Macro(name, content))
            }
        } catch (_: JSONException) {
            // Retorna lista vazia em caso de JSON malformado
        }
        return list
    }
}
