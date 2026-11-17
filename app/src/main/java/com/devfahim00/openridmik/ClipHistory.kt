package com.devfahim00.openridmik

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent clipboard history (newest first, pinned items are kept and shown on top). */
object ClipHistory {
    private const val MAX_UNPINNED = 60

    class Item(val text: String, val pinned: Boolean, val ts: Long = 0L)

    private fun sp(c: Context) = c.getSharedPreferences("openridmik_clip", Context.MODE_PRIVATE)

    private fun load(c: Context): MutableList<Item> {
        val out = ArrayList<Item>()
        try {
            val arr = JSONArray(sp(c).getString("items", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Item(o.getString("t"), o.optBoolean("p", false), o.optLong("ts", 0L)))
            }
        } catch (e: Exception) {
            // corrupted store: start over
        }
        return out
    }

    private fun save(c: Context, list: List<Item>) {
        val arr = JSONArray()
        for (item in list) {
            arr.put(JSONObject().put("t", item.text).put("p", item.pinned).put("ts", item.ts))
        }
        sp(c).edit().putString("items", arr.toString()).apply()
    }

    /** Pinned first, then the rest, each group newest first. */
    fun ordered(c: Context): List<Item> {
        val all = load(c)
        return all.filter { it.pinned } + all.filter { !it.pinned }
    }

    fun add(c: Context, text: String) {
        val list = load(c)
        val now = System.currentTimeMillis()
        if (list.isNotEmpty() && list[0].text == text) return
        val old = list.firstOrNull { it.text == text }
        list.removeAll { it.text == text }
        list.add(0, Item(text, old?.pinned ?: false, now))
        var unpinned = 0
        val trimmed = list.filter { item ->
            if (item.pinned) true else {
                unpinned++
                unpinned <= MAX_UNPINNED
            }
        }
        save(c, trimmed)
    }

    fun togglePin(c: Context, text: String) {
        save(c, load(c).map { if (it.text == text) Item(it.text, !it.pinned, it.ts) else it })
    }

    fun remove(c: Context, text: String) {
        save(c, load(c).filter { it.text != text })
    }

    fun clearUnpinned(c: Context) {
        save(c, load(c).filter { it.pinned })
    }

    fun importPinned(c: Context, texts: List<String>) {
        val list = load(c)
        val existing = list.map { it.text }.toHashSet()
        val now = System.currentTimeMillis()
        for (t in texts.reversed()) {
            if (t.isNotBlank() && t !in existing) {
                list.add(0, Item(t, true, now))
            }
        }
        save(c, list)
    }
}
