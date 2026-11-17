package com.devfahim00.openridmik

import android.content.Context
import org.json.JSONObject

/**
 * Ultra-lightweight personal text shortcuts (e.g. "salam" -> "আসসালামু আলাইকুম").
 * Kept in memory only on demand (~1 KB).
 */
object Snippets {
    private const val PREF = "openridmik_snippets"
    private var cache: LinkedHashMap<String, String>? = null

    @Synchronized
    fun all(c: Context): Map<String, String> {
        cache?.let { return it }
        val map = LinkedHashMap<String, String>()
        val sp = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val raw = sp.getString("map", null)
        if (raw == null) {
            map["salam"] = "আসসালামু আলাইকুম"
            map["ty"] = "Thank you!"
            save(c, map)
        } else {
            try {
                val o = JSONObject(raw)
                val keys = o.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = o.getString(k)
                }
            } catch (_: Exception) {}
        }
        cache = map
        return map
    }

    @Synchronized
    private fun save(c: Context, map: Map<String, String>) {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("map", o.toString()).apply()
    }

    @Synchronized
    fun add(c: Context, key: String, value: String) {
        val k = key.trim().lowercase()
        val v = value.trim()
        if (k.isEmpty() || v.isEmpty()) return
        val map = LinkedHashMap(all(c))
        map[k] = v
        cache = map
        save(c, map)
    }

    @Synchronized
    fun remove(c: Context, key: String) {
        val map = LinkedHashMap(all(c))
        if (map.remove(key) != null) {
            cache = map
            save(c, map)
        }
    }

    fun match(c: Context, word: String): String? {
        if (word.isEmpty()) return null
        return all(c)[word.trim().lowercase()]
    }
}
