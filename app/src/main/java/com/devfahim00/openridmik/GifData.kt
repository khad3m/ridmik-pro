package com.devfahim00.openridmik

import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open-source, keyless & unlimited animated GIF catalog powered by Google Noto Animated Emoji CDN.
 * Uses Android's native ImageDecoder (API 28+) for C++/Skia decoding with minimal Java heap RAM,
 * and caps disk cache at 4 MB.
 */
object GifData {

    class GifItem(val emoji: String, val code: String, val label: String)
    class Category(val name: String, val items: List<GifItem>)

    private const val MAX_CACHE_BYTES = 4L * 1024L * 1024L

    fun gifUrl(code: String): String =
        "https://fonts.gstatic.com/s/e/notoemoji/latest/$code/512.gif"

    val categories: List<Category> = listOf(
        Category(
            "Trending",
            listOf(
                GifItem("😂", "1f602", "Laughing"),
                GifItem("❤️", "2764_fe0f", "Heart"),
                GifItem("🔥", "1f525", "Fire"),
                GifItem("😭", "1f62d", "Crying"),
                GifItem("😍", "1f60d", "Heart Eyes"),
                GifItem("🤣", "1f923", "ROFL"),
                GifItem("🥰", "1f970", "Smiling Hearts"),
                GifItem("👍", "1f44d", "Thumbs Up"),
                GifItem("🎉", "1f389", "Party"),
                GifItem("😎", "1f60e", "Cool"),
                GifItem("🙏", "1f64f", "Folded Hands"),
                GifItem("💯", "1f4af", "100"),
                GifItem("🤔", "1f914", "Thinking"),
                GifItem("🥹", "1f979", "Holding Tears"),
                GifItem("🫡", "1fae1", "Salute"),
                GifItem("✨", "2728", "Sparkles"),
            ),
        ),
        Category(
            "Reactions",
            listOf(
                GifItem("😀", "1f600", "Grinning"),
                GifItem("😅", "1f605", "Sweat Smile"),
                GifItem("😉", "1f609", "Wink"),
                GifItem("😜", "1f61c", "Winking Tongue"),
                GifItem("🤪", "1f92a", "Zany"),
                GifItem("🥳", "1f973", "Partying"),
                GifItem("🤩", "1f929", "Star Struck"),
                GifItem("🤯", "1f92f", "Mind Blown"),
                GifItem("😱", "1f631", "Scream"),
                GifItem("😴", "1f634", "Sleeping"),
                GifItem("😡", "1f621", "Angry"),
                GifItem("🤮", "1f92e", "Vomiting"),
                GifItem("🫠", "1fae0", "Melting"),
                GifItem("🫥", "1fae5", "Dotted Face"),
                GifItem("🫣", "1fae3", "Peeking"),
                GifItem("😬", "1f62c", "Grimacing"),
            ),
        ),
        Category(
            "Love",
            listOf(
                GifItem("❤️", "2764_fe0f", "Red Heart"),
                GifItem("🩷", "1fa77", "Pink Heart"),
                GifItem("💔", "1f494", "Broken Heart"),
                GifItem("💖", "1f496", "Sparkling Heart"),
                GifItem("💘", "1f498", "Heart Arrow"),
                GifItem("😘", "1f618", "Blowing Kiss"),
                GifItem("🤗", "1f917", "Hugging"),
                GifItem("🫶", "1faf6", "Heart Hands"),
                GifItem("💋", "1f48b", "Kiss Mark"),
                GifItem("🌹", "1f339", "Rose"),
                GifItem("💐", "1f490", "Bouquet"),
                GifItem("🥰", "1f970", "In Love"),
            ),
        ),
        Category(
            "Celebrate",
            listOf(
                GifItem("🎉", "1f389", "Party Popper"),
                GifItem("🎊", "1f38a", "Confetti"),
                GifItem("🎂", "1f382", "Birthday Cake"),
                GifItem("🎁", "1f381", "Gift"),
                GifItem("🏆", "1f3c6", "Trophy"),
                GifItem("🥇", "1f947", "Gold Medal"),
                GifItem("🚀", "1f680", "Rocket"),
                GifItem("💥", "1f4a5", "Boom"),
                GifItem("🌟", "1f31f", "Glowing Star"),
                GifItem("🎈", "1f388", "Balloon"),
                GifItem("🎇", "1f387", "Sparkler"),
                GifItem("🎆", "1f386", "Fireworks"),
            ),
        ),
        Category(
            "Gestures",
            listOf(
                GifItem("👋", "1f44b", "Wave"),
                GifItem("👏", "1f44f", "Clap"),
                GifItem("🙌", "1f64c", "Raising Hands"),
                GifItem("🤝", "1f91d", "Handshake"),
                GifItem("✌️", "270c_fe0f", "Victory"),
                GifItem("🤞", "1f91e", "Crossed Fingers"),
                GifItem("👌", "1f44c", "OK Hand"),
                GifItem("🤙", "1f919", "Call Me"),
                GifItem("💪", "1f4aa", "Muscle"),
                GifItem("👎", "1f44e", "Thumbs Down"),
                GifItem("🤦", "1f926", "Facepalm"),
                GifItem("🤷", "1f937", "Shrug"),
            ),
        ),
        Category(
            "Animals",
            listOf(
                GifItem("🐶", "1f436", "Dog"),
                GifItem("🐱", "1f431", "Cat"),
                GifItem("🦊", "1f98a", "Fox"),
                GifItem("🐼", "1f43c", "Panda"),
                GifItem("🦁", "1f981", "Lion"),
                GifItem("🐸", "1f438", "Frog"),
                GifItem("🦋", "1f98b", "Butterfly"),
                GifItem("🐢", "1f422", "Turtle"),
                GifItem("🐧", "1f427", "Penguin"),
                GifItem("🐝", "1f41d", "Bee"),
                GifItem("🦄", "1f984", "Unicorn"),
                GifItem("🐣", "1f423", "Chick"),
            ),
        ),
    )

    private fun cacheDir(c: Context): File = File(c.cacheDir, "gifs").apply { mkdirs() }

    fun cachedFile(c: Context, code: String): File = File(cacheDir(c), "$code.gif")

    /** Downloads the GIF into cacheDir/gifs/ if not already cached; returns null on network error. */
    fun fetchGifFile(c: Context, code: String): File? {
        val target = cachedFile(c, code)
        if (target.exists() && target.length() > 100) {
            target.setLastModified(System.currentTimeMillis())
            return target
        }
        val tmp = File(cacheDir(c), "$code.gif.tmp")
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(gifUrl(code)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.use { inp ->
                tmp.outputStream().use { out -> inp.copyTo(out, 8192) }
            }
            if (tmp.length() < 100) {
                tmp.delete()
                return null
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            pruneCache(c)
            target
        } catch (_: Exception) {
            tmp.delete()
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    /** Decodes a small 96x96 AnimatedImageDrawable on API 28+ (lowest RAM, native Skia). */
    fun decodePreview(file: File): Drawable? {
        if (Build.VERSION.SDK_INT < 28) return null
        return try {
            val src = ImageDecoder.createSource(file)
            ImageDecoder.decodeDrawable(src) { decoder, _, _ ->
                decoder.setTargetSize(96, 96)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun pruneCache(c: Context) {
        val dir = cacheDir(c)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".gif") } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_CACHE_BYTES) return
        files.sortBy { it.lastModified() }
        for (f in files) {
            if (total <= MAX_CACHE_BYTES * 3 / 4) break
            val len = f.length()
            if (f.delete()) total -= len
        }
    }

    fun stopDrawable(d: Drawable?) {
        if (Build.VERSION.SDK_INT >= 28 && d is AnimatedImageDrawable) {
            try { d.stop() } catch (_: Exception) {}
        }
    }
}
