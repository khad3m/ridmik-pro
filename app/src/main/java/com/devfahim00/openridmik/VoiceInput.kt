package com.devfahim00.openridmik

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean

/** Result of one Gemini voice request. */
sealed class VoiceResult {
    /** Recognised text; empty when the audio contained no speech. */
    class Text(val text: String) : VoiceResult()
    /** Human-readable problem, shown to the user as a toast. [modelProblem] lets the caller retry with another model. */
    class Error(val message: String, val modelProblem: Boolean = false, val retryBare: Boolean = false) : VoiceResult()
}

object GeminiVoice {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    private const val PROMPT =
        "You are a speech-to-text engine. Transcribe the spoken audio exactly as said. " +
        "The speech may be in Bangla (Bengali), English, or a mix of both: keep every word in the " +
        "language it was spoken in and NEVER translate. " +
        "Add natural punctuation, spacing and sentence capitalisation. " +
        "Return ONLY the transcription as plain text: no explanations, no quotes, no labels. " +
        "If the audio contains no speech, return an empty string."

    /**
     * Transcribes [wav] (WAV bytes) with the user's Gemini key. [fallbacks] are other models the
     * user fetched from Google earlier; they are tried only when the chosen model itself is
     * rejected. Key / network / quota problems are returned as-is.
     */
    fun transcribe(apiKey: String, model: String, wav: ByteArray, fallbacks: List<String> = emptyList()): VoiceResult {
        val chain = mutableListOf(model)
        for (m in fallbacks) if (m != model && m !in chain) chain.add(m)
        var last = "Voice input failed"
        for (m in chain) {
            when (val r = post(apiKey, m, audioBody(wav, m))) {
                is VoiceResult.Text -> return r
                is VoiceResult.Error -> if (r.modelProblem) last = r.message else return r
            }
        }
        return VoiceResult.Error(last)
    }

    /** Model catalogue of the key, fetched live from Google - nothing is hardcoded. */
    sealed class ModelList {
        class Ok(val models: List<String>) : ModelList()
        class Fail(val message: String) : ModelList()
    }

    /** Speech-generation / non-input models that make no sense as a transcription target. */
    private val NON_INPUT = listOf("tts", "imagen", "image-gen", "veo", "embed", "aqa", "gemma", "learnlm", "live")

    /** GET /models: only models that accept generateContent, newest catalogue straight from Google. */
    fun fetchModels(apiKey: String): ModelList {
        val first = fetchModelsOnce(apiKey, "models?pageSize=1000")
        if (first is ModelList.Ok) return first
        // some API versions reject pageSize - retry with the plain endpoint
        return fetchModelsOnce(apiKey, "models")
    }

    private fun fetchModelsOnce(apiKey: String, path: String): ModelList {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 12000
            conn.readTimeout = 20000
            conn.setRequestProperty("x-goog-api-key", cleanKey(apiKey))
            val code = conn.responseCode
            if (code !in 200..299) {
                val msg = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
                return ModelList.Fail((classify(code, msg ?: "") as VoiceResult.Error).message)
            }
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val arr = o.optJSONArray("models") ?: return ModelList.Ok(emptyList())
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val m = arr.getJSONObject(i)
                val name = m.optString("name").removePrefix("models/")
                if (name.isEmpty()) continue
                val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
                var gen = false
                for (j in 0 until methods.length()) if (methods.optString(j) == "generateContent") gen = true
                if (!gen) continue
                val low = name.lowercase()
                if (NON_INPUT.any { low.contains(it) }) continue
                out.add(name)
            }
            out.sort()
            ModelList.Ok(out)
        } catch (e: UnknownHostException) {
            ModelList.Fail("No internet connection")
        } catch (e: SocketTimeoutException) {
            ModelList.Fail("Network too slow - try again")
        } catch (e: Exception) {
            ModelList.Fail("Network error: ${e.message ?: "unknown"}")
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    /** Minimal text-only check: null when the key + model work, otherwise the exact reason. */
    fun ping(apiKey: String, model: String): String? {
        // no generationConfig at all - every generateContent model accepts a bare contents request
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "Reply with exactly: OK")))),
            )
        return when (val r = post(apiKey, model, body)) {
            is VoiceResult.Text -> null
            is VoiceResult.Error -> r.message
        }
    }

    /** Audio goes as two parts (prompt + inline WAV); a Part carries only one field, so they must not be merged. */
    private fun audioBody(wav: ByteArray, model: String): JSONObject {
        val b64 = Base64.encodeToString(wav, Base64.NO_WRAP)
        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", PROMPT))
                            .put(
                                JSONObject().put(
                                    "inline_data",
                                    JSONObject().put("mime_type", "audio/wav").put("data", b64),
                                ),
                            ),
                    ),
                ),
            )
            .put("generationConfig", generationConfig(model, 800))
    }

    /** 2.5 models think by default; thinking is skipped because transcription must be instant. */
    private fun generationConfig(model: String, maxTokens: Int): JSONObject {
        val gen = JSONObject().put("temperature", 0).put("maxOutputTokens", maxTokens)
        if (model.startsWith("gemini-2.5")) {
            gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
        }
        return gen
    }

    /** Pastes often carry stray spaces, newlines or smart quotes; the header must be the bare key. */
    private fun cleanKey(k: String) = k.replace(Regex("[\\s\"“”‘’]+"), "")

    private fun post(apiKey: String, model: String, body: JSONObject): VoiceResult {
        val r = postOnce(apiKey, model, body)
        if (r is VoiceResult.Error && r.retryBare) {
            // some models reject extra generation fields (thinkingConfig, temperature, ...):
            // one bare-bones retry with just the contents keeps voice typing working anyway
            val bare = JSONObject().put("contents", body.optJSONArray("contents") ?: JSONArray())
            val r2 = postOnce(apiKey, model, bare)
            if (r2 is VoiceResult.Text) return r2
        }
        return r
    }

    private fun postOnce(apiKey: String, model: String, body: JSONObject): VoiceResult {
        val conn = URL(BASE + model + ":generateContent").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 12000
            conn.readTimeout = 40000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", cleanKey(apiKey))
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val msg = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) {
                    null
                }
                return classify(code, msg ?: "")
            }
            parseText(conn.inputStream.bufferedReader().use { it.readText() })
        } catch (e: UnknownHostException) {
            VoiceResult.Error("No internet connection")
        } catch (e: SocketTimeoutException) {
            VoiceResult.Error("Network too slow - try again")
        } catch (e: Exception) {
            VoiceResult.Error("Network error: ${e.message ?: "unknown"}")
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    /** A rejected model (404 / "not supported") may fall back to the next one; other errors are final. */
    private fun classify(code: Int, body: String): VoiceResult {
        val msg = try {
            JSONObject(body).optJSONObject("error")?.optString("message") ?: body.take(140)
        } catch (e: Exception) {
            body.take(140)
        }
        val m = msg.lowercase()
        return when {
            m.contains("api key not valid") || m.contains("api_key_invalid") ->
                VoiceResult.Error("Invalid API key - check it in Ridmik Pro settings")
            // region blocks arrive as 400 with this text - must be checked before any "not supported" model logic
            m.contains("location is not supported") || m.contains("not supported for the api use") ->
                VoiceResult.Error(
                    "Google blocks the Gemini API from your region/network. The key is fine - try another network (or VPN), then retry.",
                )
            code == 429 || m.contains("quota") || m.contains("resource_exhausted") ->
                VoiceResult.Error("Free quota reached - wait a minute (rate limit) or a day (daily limit)")
            code == 401 || code == 403 || m.contains("permission denied") ->
                VoiceResult.Error("Key rejected (403) - check the key restrictions in Google AI Studio")
            // show Google's actual message so the cause is never hidden; the caller retries bare once
            code == 400 ->
                VoiceResult.Error("Bad request (400): ${msg.take(160)}", retryBare = true)
            code == 404 || m.contains("not found") || m.contains("not supported") || m.contains("does not support") ->
                VoiceResult.Error("Model unavailable - trying the next one", modelProblem = true)
            code >= 500 ->
                VoiceResult.Error("Gemini server busy - try again")
            else ->
                VoiceResult.Error("Failed (HTTP $code): ${msg.take(100)}")
        }
    }

    /** Joins the candidate's text parts and strips stray markdown wrapping. */
    private fun parseText(raw: String): VoiceResult {
        return try {
            val o = JSONObject(raw)
            val cands = o.optJSONArray("candidates")
            if (cands == null || cands.length() == 0) {
                val block = o.optJSONObject("promptFeedback")?.optString("blockReason")
                return if (block.isNullOrEmpty()) VoiceResult.Text("") else VoiceResult.Error("Audio was blocked: $block")
            }
            val sb = StringBuilder()
            val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    sb.append(parts.getJSONObject(i).optString("text"))
                }
            }
            VoiceResult.Text(clean(sb.toString()))
        } catch (e: Exception) {
            VoiceResult.Error("Unexpected reply from Gemini")
        }
    }

    private fun clean(t: String): String {
        var s = t.trim()
        if (s.startsWith("```")) s = s.removePrefix("```").trim().removePrefix("json").trim()
        if (s.endsWith("```")) s = s.removeSuffix("```").trim()
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') s = s.substring(1, s.length - 1)
        return s.trim()
    }
}

/** 16-bit PCM in a little-endian RIFF/WAV container, as accepted by the Gemini audio input. */
object Wav {
    fun encode(pcm: ByteArray, sampleRate: Int, channels: Int = 1, bits: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val b = ByteArrayOutputStream(44 + pcm.size)
        fun le32(v: Int) {
            b.write(v and 0xFF); b.write(v shr 8 and 0xFF); b.write(v shr 16 and 0xFF); b.write(v shr 24 and 0xFF)
        }
        fun le16(v: Int) { b.write(v and 0xFF); b.write(v shr 8 and 0xFF) }
        fun tag(s: String) { for (c in s.toByteArray(Charsets.US_ASCII)) b.write(c.toInt()) }
        tag("RIFF"); le32(pcm.size + 36); tag("WAVE")
        tag("fmt "); le32(16); le16(1); le16(channels); le32(sampleRate); le32(byteRate); le16(channels * bits / 8); le16(bits)
        tag("data"); le32(pcm.size); b.write(pcm)
        return b.toByteArray()
    }
}

/**
 * Records mono 16 kHz PCM from the microphone and stops by itself after a pause
 * (1.8 s of quiet once speech was heard) or a 25 s hard cap, so voice typing
 * needs exactly one tap. [onStopped] runs on the recorder thread with the WAV
 * bytes, or null + a short reason when nothing usable was captured.
 */
class VoiceRecorder(private val onStopped: (wav: ByteArray?, reason: String?) -> Unit) {

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val stopRequested = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    @SuppressLint("MissingPermission") // the service checks RECORD_AUDIO before starting us
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            onStopped(null, "Microphone init failed")
            return
        }
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf * 2, 16384),
            )
        } catch (e: Exception) {
            onStopped(null, "Microphone unavailable (another app may be using it)")
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            onStopped(null, "Microphone unavailable")
            return
        }
        record = rec
        stopRequested.set(false)
        cancelled.set(false)
        rec.startRecording()
        thread = Thread({
            var pcm: ByteArray? = null
            var err: String? = null
            try {
                pcm = loop(rec)
            } catch (e: Exception) {
                err = e.message ?: "Recording failed"
            }
            try { rec.stop() } catch (_: Exception) {}
            try { rec.release() } catch (_: Exception) {}
            record = null
            val wav = pcm?.takeIf { it.size > MIN_BYTES }?.let { Wav.encode(it, SAMPLE_RATE) }
            if (!cancelled.get()) {
                onStopped(wav, err ?: if (wav == null) "No speech captured" else null)
            }
        }, "ridmik-voice").apply { priority = Thread.MAX_PRIORITY; start() }
    }

    /** Manual stop (mic tapped again): keep the audio gathered so far. */
    fun stop() = stopRequested.set(true)

    /** Abort: the result is discarded and [onStopped] never fires. */
    fun cancel() {
        cancelled.set(true)
        stopRequested.set(true)
    }

    private fun loop(rec: AudioRecord): ByteArray {
        val buf = ShortArray(1600) // 100 ms frames at 16 kHz
        val out = ByteArrayOutputStream()
        var speech = false
        var quietMs = 0
        var totalMs = 0
        while (!stopRequested.get() && totalMs < MAX_MS) {
            val n = rec.read(buf, 0, buf.size)
            if (n <= 0) {
                Thread.sleep(8)
                continue
            }
            var sum = 0.0
            for (i in 0 until n) {
                val s = buf[i].toDouble()
                sum += s * s
            }
            val rms = Math.sqrt(sum / n).toFloat()
            for (i in 0 until n) {
                val v = buf[i].toInt()
                out.write(v and 0xFF)
                out.write(v shr 8 and 0xFF)
            }
            totalMs += 100
            if (rms > SILENCE_RMS) {
                speech = true
                quietMs = 0
            } else if (speech) {
                quietMs += 100
                if (quietMs >= SILENCE_MS) break
            }
        }
        return out.toByteArray()
    }

    companion object {
        const val SAMPLE_RATE = 16000
        /** ~ -23 dBFS; below this the mic counts as quiet. */
        const val SILENCE_RMS = 450f
        const val SILENCE_MS = 1800
        const val MAX_MS = 25000
        /** ~0.1 s of audio, the smallest amount sent to the API. */
        const val MIN_BYTES = 3200
    }
}
