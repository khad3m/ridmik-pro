package com.devfahim00.openridmik

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * On-device learning of the user's own typing: word frequencies and word-pair (bigram) counts.
 * Stored in a dedicated file (avoiding SharedPreferences ANR stalls) and capped for low RAM.
 */
object UserDict {
    private const val LEGACY_PREFS = "openridmik_userdict"
    private const val FILE_NAME = "userdict.json"
    private const val MAX_WORDS = 2000
    private const val MAX_PAIRS = 4000
    private const val SAVE_EVERY = 8

    private val words = HashMap<String, Int>()
    private val pairs = HashMap<String, Int>() // "prev\u0001next" -> count
    private var loaded = false
    private var dirty = 0

    /** Latin words are stored lower-case so "The" and "the" count together. */
    fun norm(w: String): String = if (w.isNotEmpty() && (w[0] in 'a'..'z' || w[0] in 'A'..'Z')) w.lowercase() else w

    private fun file(c: Context) = File(c.applicationContext.filesDir, FILE_NAME)

    @Synchronized
    private fun ensure(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c)
            val raw = if (f.exists()) {
                f.readText()
            } else {
                val sp = c.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                val leg = sp.getString("data", null)
                if (!leg.isNullOrEmpty()) sp.edit().remove("data").apply()
                leg ?: "{}"
            }
            val o = JSONObject(raw)
            o.optJSONObject("w")?.let { j -> j.keys().forEach { words[it] = j.getInt(it) } }
            o.optJSONObject("p")?.let { j -> j.keys().forEach { pairs[it] = j.getInt(it) } }
        } catch (e: Exception) {
            words.clear()
            pairs.clear()
        }
    }

    @Synchronized
    fun save(c: Context) {
        if (!loaded || dirty == 0) return
        dirty = 0
        val w = HashMap(words)
        val p = HashMap(pairs)
        val app = c.applicationContext
        Thread {
            try {
                val o = JSONObject()
                o.put("w", JSONObject(w as Map<*, *>))
                o.put("p", JSONObject(p as Map<*, *>))
                val target = file(app)
                val tmp = File(app.filesDir, "$FILE_NAME.tmp")
                tmp.writeText(o.toString())
                if (!tmp.renameTo(target)) target.writeText(o.toString())
            } catch (e: Exception) {
                CrashLog.record(app, "UserDict.save", e)
            }
        }.start()
    }

    private fun prune(map: HashMap<String, Int>, max: Int) {
        if (map.size <= max) return
        val keep = map.entries.sortedByDescending { it.value }.take(max * 3 / 4)
        val m = keep.associate { it.key to it.value }
        map.clear()
        map.putAll(m)
    }

    @Synchronized
    fun learn(c: Context, prev0: String?, word0: String) {
        ensure(c)
        val word = norm(word0)
        val prev = prev0?.let { norm(it) }
        if (word.length < 2) return
        words[word] = (words[word] ?: 0) + 1
        if (!prev.isNullOrEmpty()) {
            val k = "$prev\u0001$word"
            pairs[k] = (pairs[k] ?: 0) + 1
        }
        prune(words, MAX_WORDS)
        prune(pairs, MAX_PAIRS)
        if (++dirty >= SAVE_EVERY) save(c)
    }

    /** Words the user has typed that start with [prefix], most relevant first (prev-word pairs boosted). */
    @Synchronized
    fun suggest(c: Context, prefix0: String, prev0: String?, max: Int): List<String> {
        ensure(c)
        if (prefix0.isEmpty()) return emptyList()
        val prefix = norm(prefix0)
        val prev = prev0?.let { norm(it) }
        return words.entries.asSequence()
            .filter { it.key.startsWith(prefix) && it.key != prefix }
            .sortedByDescending { it.value + 5 * (if (prev.isNullOrEmpty()) 0 else pairs["$prev\u0001${it.key}"] ?: 0) }
            .take(max)
            .map { it.key }
            .toList()
    }

    /** Most likely words to follow [prev]. */
    @Synchronized
    fun next(c: Context, prev0: String, max: Int): List<String> {
        ensure(c)
        if (prev0.isEmpty()) return emptyList()
        val prev = norm(prev0)
        val pre = "$prev\u0001"
        return pairs.entries.asSequence()
            .filter { it.key.startsWith(pre) }
            .sortedByDescending { it.value }
            .take(max)
            .map { it.key.substring(pre.length) }
            .toList()
    }

    @Synchronized
    fun exportJson(c: Context): JSONObject {
        ensure(c)
        return JSONObject()
            .put("w", JSONObject(words as Map<*, *>))
            .put("p", JSONObject(pairs as Map<*, *>))
    }

    @Synchronized
    fun importJson(c: Context, o: JSONObject) {
        ensure(c)
        o.optJSONObject("w")?.let { j -> j.keys().forEach { k -> words[k] = maxOf(words[k] ?: 0, j.optInt(k, 1)) } }
        o.optJSONObject("p")?.let { j -> j.keys().forEach { k -> pairs[k] = maxOf(pairs[k] ?: 0, j.optInt(k, 1)) } }
        prune(words, MAX_WORDS)
        prune(pairs, MAX_PAIRS)
        dirty++
        save(c)
    }
}
