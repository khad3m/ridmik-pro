package com.devfahim00.openridmik

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.ApplicationExitInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : Activity() {

    /** Small preview card of a keyboard theme. */
    private class Swatch(ctx: Context, val t: KbTheme, val selected: Boolean) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val d = ctx.resources.displayMetrics.density

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            p.style = Paint.Style.FILL
            p.color = t.bg
            canvas.drawRoundRect(RectF(0f, 0f, w, h), 12 * d, 12 * d, p)
            val pad = 8 * d
            val gap = 4 * d
            val kw = (w - 2 * pad - 3 * gap) / 4
            val kh = (h - 2 * pad - gap) / 2
            for (r in 0..1) {
                for (c in 0..3) {
                    p.color = when {
                        r == 1 && c == 3 -> t.accent
                        r == 1 && c == 0 -> t.special
                        else -> t.key
                    }
                    val l = pad + c * (kw + gap)
                    val tp = pad + r * (kh + gap)
                    canvas.drawRoundRect(RectF(l, tp, l + kw, tp + kh), 4 * d, 4 * d, p)
                }
            }
            if (selected) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3 * d
                p.color = t.accent
                canvas.drawRoundRect(RectF(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d), 12 * d, 12 * d, p)
            }
        }
    }

    private lateinit var swatchRow: LinearLayout
    private val heightBars = ArrayList<SeekBar>()
    private var batteryStatus: TextView? = null
    private var imeStatus: TextView? = null
    private lateinit var apiKeyEdit: EditText
    private lateinit var modelEdit: EditText

    private fun isUnrestricted(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun imeSetupState(): String {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        val active = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        return when {
            active -> "Active ✓"
            enabled -> "Enabled"
            else -> "Off"
        }
    }

    override fun onResume() {
        super.onResume()
        batteryStatus?.text = if (isUnrestricted()) "Unrestricted ✓" else "Restricted"
        imeStatus?.text = imeSetupState()
    }

    override fun onPause() {
        super.onPause()
        // keep the Gemini key and model even when the user leaves without pressing Save
        if (::apiKeyEdit.isInitialized) Prefs.setString(this, "gemini_key", apiKeyEdit.text.toString().trim())
        if (::modelEdit.isInitialized) Prefs.setString(this, "gemini_model", modelEdit.text.toString().trim().removePrefix("models/"))
    }
    private val d by lazy { resources.displayMetrics.density }

    private fun dp(v: Int) = (v * d).toInt()

    private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)

    private fun currentVersion(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    private fun parseColor(s: String): Int? {
        val t = s.trim()
        if (t.isEmpty()) return null
        return try {
            Color.parseColor(if (t.startsWith("#")) t else "#$t") or 0xFF000000.toInt()
        } catch (e: Exception) {
            null
        }
    }

    private fun rounded(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(r).toFloat()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bg = getColor(R.color.kb_bg)
        val surface = getColor(R.color.key_bg)
        val line = getColor(R.color.key_special_bg)
        val fg = getColor(R.color.key_text)
        val muted = getColor(R.color.key_hint)
        val accent = getColor(R.color.accent)
        val onAccent = getColor(R.color.accent_text)

        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!Themes.isNight(this)) {
            @Suppress("DEPRECATION")
            var flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            window.decorView.systemUiVisibility = flags
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(28))
        }

        fun lp(top: Int = 0) = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(top) }

        fun text(t: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        fun section(title: String) {
            root.addView(
                text(title.uppercase(), 12f, muted, bold = true).apply {
                    letterSpacing = 0.08f
                    setPadding(dp(8), 0, 0, 0)
                },
                lp(24).apply { bottomMargin = dp(8) },
            )
        }

        fun card(): LinearLayout {
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(surface)
                    cornerRadius = dp(16).toFloat()
                    setStroke(1, line)
                }
            }
            root.addView(c, lp())
            return c
        }

        fun divider(c: LinearLayout) {
            c.addView(
                View(this).apply { setBackgroundColor(line) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                    leftMargin = dp(16)
                },
            )
        }

        fun row(c: LinearLayout, first: Boolean, title: String, trailing: View? = null, onClick: (() -> Unit)? = null): TextView {
            if (!first) divider(c)
            val label = text(title, 16f, fg)
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(52)
                setPadding(dp(16), 0, dp(16), 0)
                addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (trailing != null) addView(trailing)
                if (onClick != null) {
                    isClickable = true
                    setOnClickListener { onClick() }
                }
            }
            c.addView(r, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            return label
        }

        fun chevron() = text("›", 22f, muted)

        fun toggleRow(c: LinearLayout, first: Boolean, title: String, key: String) {
            val sw = Switch(this).apply {
                isChecked = Prefs.get(this@SettingsActivity, key)
                setOnCheckedChangeListener { _, on ->
                    Prefs.set(this@SettingsActivity, key, on)
                    OpenRidmikService.live?.reloadFromPrefs()
                }
            }
            row(c, first, title, sw) { sw.toggle() }
        }

        // ---- header ----
        root.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), 0, dp(8), dp(4))
                addView(
                    LinearLayout(this@SettingsActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        addView(text("Ridmik Pro", 30f, fg, bold = true))
                        addView(
                            text("v${currentVersion()}", 11f, accent, bold = true).apply {
                                background = rounded(line, 8)
                                setPadding(dp(8), dp(3), dp(8), dp(3))
                            },
                            LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                            ).apply { leftMargin = dp(10) },
                        )
                    },
                )
                addView(
                    text("Bangla + English · Gemini Voice · Privacy-first", 13f, muted).apply {
                        setPadding(0, dp(4), 0, 0)
                    },
                )
            },
            lp(),
        )

        // ---- keyboard ----
        section("Keyboard")
        val setup = card()
        imeStatus = text(imeSetupState(), 13f, accent, bold = true)
        row(setup, true, "Enable Ridmik Pro", imeStatus) {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        row(setup, false, "Switch keyboard", chevron()) {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        // ---- layout (segmented) ----
        section("Bangla layout")
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(surface, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        root.addView(seg, lp())
        fun refreshSeg() {
            seg.removeAllViews()
            for ((id, name) in listOf("avro" to "Avro", "probhat" to "Probhat", "jatiyo" to "জাতীয়")) {
                val on = Prefs.layout(this) == id
                seg.addView(
                    text(name, 14f, if (on) onAccent else fg, bold = on).apply {
                        gravity = Gravity.CENTER
                        background = if (on) rounded(accent, 10) else null
                        setOnClickListener {
                            Prefs.setLayout(this@SettingsActivity, id)
                            refreshSeg()
                            OpenRidmikService.live?.reloadFromPrefs()
                        }
                    },
                    LinearLayout.LayoutParams(0, dp(42), 1f),
                )
            }
        }
        refreshSeg()

        // ---- theme ----
        section("Theme")
        swatchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(swatchRow)
            },
            lp(),
        )
        refreshSwatches(fg, muted)

        val customBox = card().apply { visibility = View.GONE }
        val cur = Themes.current(this)
        val fields = ArrayList<EditText>()
        fun field(name: String, initial: Int, first: Boolean) {
            val e = EditText(this).apply {
                setText(hex(initial))
                setTextColor(fg)
                textSize = 15f
                gravity = Gravity.END
                background = null
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setSingleLine()
                minWidth = dp(110)
            }
            fields.add(e)
            row(customBox, first, name, e)
        }
        field("Background", Prefs.customColor(this, "bg", cur.bg), true)
        field("Keys", Prefs.customColor(this, "key", cur.key), false)
        field("Text", Prefs.customColor(this, "text", cur.text), false)
        field("Accent", Prefs.customColor(this, "accent", cur.accent), false)
        row(customBox, false, "Apply custom theme") {
            val v = fields.map { parseColor(it.text.toString()) }
            if (v.any { it == null }) {
                Toast.makeText(this, "Use colours like #1A73E8", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setCustomColors(this, v[0]!!, v[1]!!, v[2]!!, v[3]!!)
                Prefs.setTheme(this, "custom")
                refreshSwatches(fg, muted)
                OpenRidmikService.live?.reloadFromPrefs()
                Toast.makeText(this, "Custom theme applied", Toast.LENGTH_SHORT).show()
            }
        }.setTextColor(accent)
        // toggle row sits above the (collapsed) custom colours card
        val customToggle = text("Custom colours", 14f, accent, bold = true).apply {
            setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener {
                customBox.visibility = if (customBox.visibility == View.GONE) View.VISIBLE else View.GONE
            }
        }
        root.addView(customToggle, root.indexOfChild(customBox), lp())

        // ---- layouts ----
        section("Keyboard layouts")
        val langs = card()
        fun langRow(first: Boolean, title: String, key: String) {
            val sw = Switch(this).apply {
                isChecked = Prefs.get(this@SettingsActivity, key)
                setOnCheckedChangeListener { b, on ->
                    Prefs.set(this@SettingsActivity, key, on)
                    // at least one layout has to stay on
                    if (listOf("lang_en", "lang_avro", "lang_probhat", "lang_jatiyo").none { Prefs.get(this@SettingsActivity, it) }) {
                        Prefs.set(this@SettingsActivity, key, true)
                        b.isChecked = true
                        Toast.makeText(this@SettingsActivity, "Keep at least one layout on", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            row(langs, first, title, sw) { sw.toggle() }
        }
        langRow(true, "English", "lang_en")
        langRow(false, "Avro", "lang_avro")
        langRow(false, "Probhat", "lang_probhat")
        langRow(false, "Jatiyo · জাতীয়", "lang_jatiyo")
        root.addView(
            text("Hold the space bar and slide left or right to switch between the layouts that are on.", 12f, muted),
            lp(6),
        )

        // ---- keyboard height ----
        section("Keyboard height")
        val hcard = card()
        fun heightRow(first: Boolean, title: String, landscape: Boolean) {
            if (!first) divider(hcard)
            val value = text("", 14f, muted)
            val head = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(text(title, 16f, fg), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(value)
            }
            val bar = SeekBar(this).apply {
                max = Prefs.HEIGHT_MAX - Prefs.HEIGHT_MIN
                progress = Prefs.heightPercent(this@SettingsActivity, landscape) - Prefs.HEIGHT_MIN
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                        val pct = Prefs.HEIGHT_MIN + p
                        value.text = "$pct%"
                        if (fromUser) {
                            Prefs.setHeightPercent(this@SettingsActivity, landscape, pct)
                            OpenRidmikService.live?.reloadFromPrefs()
                        }
                    }
                    override fun onStartTrackingTouch(sb: SeekBar) {}
                    override fun onStopTrackingTouch(sb: SeekBar) {}
                })
            }
            value.text = "${Prefs.HEIGHT_MIN + bar.progress}%"
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(8))
                addView(head)
                addView(bar)
            }
            hcard.addView(box, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            heightBars.add(bar)
        }
        heightRow(true, "Portrait", false)
        heightRow(false, "Landscape", true)
        row(hcard, false, "Reset to default", chevron()) {
            Prefs.setHeightPercent(this, false, 100)
            Prefs.setHeightPercent(this, true, 100)
            for (b in heightBars) b.progress = 100 - Prefs.HEIGHT_MIN
            OpenRidmikService.live?.reloadFromPrefs()
        }
        root.addView(
            text("Makes the keys taller or shorter. Applies the next time the keyboard opens.", 12f, muted),
            lp(6),
        )

        // ---- preferences ----
        section("Preferences")
        val prefs = card()
        toggleRow(prefs, true, "Vibrate on keypress", "haptic")
        toggleRow(prefs, false, "Sound on keypress", "sound")
        toggleRow(prefs, false, "Number row", "numberRow")
        toggleRow(prefs, false, "Bangla digits", "banglaDigits")
        toggleRow(prefs, false, "Word suggestions", "suggestions")
        toggleRow(prefs, false, "Double-space for period", "doubleSpacePeriod")
        toggleRow(prefs, false, "Key preview popup", "keyPreview")
        toggleRow(prefs, false, "Auto-pair brackets & quotes", "autoPair")
        toggleRow(prefs, false, "Key borders", "keyBorders")
        toggleRow(prefs, false, "Clipboard history", "clipboard")
        row(prefs, false, "Clear clipboard history", chevron()) {
            ClipHistory.clearUnpinned(this)
            Toast.makeText(this, "Cleared (pinned items kept)", Toast.LENGTH_SHORT).show()
        }

        // ---- text shortcuts ----
        section("Text shortcuts")
        val scard = card()
        fun refreshSnippets() {
            scard.removeAllViews()
            val all = Snippets.all(this)
            row(scard, true, "+ Add shortcut", chevron()) {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(20), dp(10), dp(20), 0)
                }
                val kEd = EditText(this).apply { hint = "Shortcut (e.g. @@, addr)" }
                val vEd = EditText(this).apply { hint = "Full text" }
                box.addView(kEd)
                box.addView(vEd)
                AlertDialog.Builder(this)
                    .setTitle("New text shortcut")
                    .setView(box)
                    .setPositiveButton("Save") { _, _ ->
                        val k = kEd.text.toString().trim()
                        val v = vEd.text.toString().trim()
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            Snippets.add(this, k, v)
                            refreshSnippets()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }.setTextColor(accent)
            for ((k, v) in all) {
                row(scard, false, "$k  →  $v", text("✕", 15f, muted)) {
                    Snippets.remove(this, k)
                    refreshSnippets()
                }
            }
        }
        refreshSnippets()

        // ---- backup & restore ----
        section("Backup & restore")
        val bcard = card()
        row(bcard, true, "Export backup (JSON)", chevron()) {
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "ridmik-pro-backup.json")
            }
            startActivityForResult(i, 901)
        }
        row(bcard, false, "Import backup (JSON)", chevron()) {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            startActivityForResult(i, 902)
        }

        // ---- app update ----
        section("App update")
        val curVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (_: Exception) { "?" }
        val ucard = card()
        var updateBusy = false
        var updateLabel: TextView? = null
        val checkRow = row(ucard, true, "Check for update", chevron()) {
            val label = updateLabel ?: return@row
            if (updateBusy) return@row
            updateBusy = true
            label.text = "Checking for update…"
            Thread {
                try {
                    val latest = fetchLatestRelease()
                    if (latest == null) {
                        Toast.makeText(this, "Could not reach GitHub — check your internet", Toast.LENGTH_SHORT).show()
                        runOnUiThread { label.text = "Check for update" }
                        return@Thread
                    }
                    val (tag, apkUrl) = latest
                    if (!isNewerVersion(tag, curVersion)) {
                        runOnUiThread {
                            label.text = "Up to date ($curVersion) ✓"
                            Toast.makeText(this, "Ridmik Pro $curVersion is the latest version", Toast.LENGTH_SHORT).show()
                        }
                        return@Thread
                    }
                    if (!packageManager.canRequestPackageInstalls()) {
                        Toast.makeText(
                            this,
                            "Allow \"Install unknown apps\" for Ridmik Pro, then tap Check again",
                            Toast.LENGTH_LONG,
                        ).show()
                        try {
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")),
                            )
                        } catch (_: Exception) {}
                        runOnUiThread { label.text = "Install permission needed — tap to retry" }
                        return@Thread
                    }
                    val apk = File(cacheDir, "update.apk")
                    runOnUiThread { label.text = "Downloading $tag…" }
                    downloadApk(apkUrl, apk) { pct ->
                        runOnUiThread { label.text = "Downloading $tag… $pct%" }
                    }
                    runOnUiThread { label.text = "Installing $tag…" }
                    installApk(apk)
                    runOnUiThread { label.text = "Check for update" }
                    Toast.makeText(this, "Confirm the $tag install in the system dialog", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    CrashLog.record(this, "inAppUpdate", e)
                    Toast.makeText(this, "Update failed: ${e.message ?: "error"}", Toast.LENGTH_SHORT).show()
                    runOnUiThread { label.text = "Check for update" }
                } finally {
                    updateBusy = false
                }
            }.start()
        }
        updateLabel = checkRow
        row(ucard, false, "Current version: $curVersion")

        // ---- keyboard keeps switching? ----
        section("Keyboard keeps switching?")
        val diag = card()
        batteryStatus = text("", 14f, muted)
        row(diag, true, "Disable battery optimization", batteryStatus) {
            if (isUnrestricted()) {
                Toast.makeText(this, "Already unrestricted", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    // system dialog: one tap on "Allow"
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")),
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        }
        row(diag, false, "App battery / autostart settings (Xiaomi etc.)", chevron()) {
            // Xiaomi/HyperOS keeps its own autostart + "No restrictions" switch; fall back to the app info page
            val tries = listOf(
                Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST").setPackage("com.miui.powerkeeper"),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            )
            for (i in tries) {
                try {
                    startActivity(i)
                    break
                } catch (e: Exception) {
                }
            }
        }
        row(diag, false, "Why did the keyboard stop?", chevron()) {
            AlertDialog.Builder(this)
                .setTitle("Keyboard stop reasons")
                .setMessage(diagnostics())
                .setPositiveButton("OK", null)
                .setNeutralButton("Clear") { _, _ -> CrashLog.clear(this) }
                .show()
        }
        root.addView(
            text(
                "If the system switches to another keyboard after long use, it is usually because the phone killed the keyboard to save battery or memory. Set Ridmik Pro to \"No restrictions\" / allow autostart, and keep it out of the recents-clear list.",
                12f, muted,
            ),
            lp(6),
        )

        // ---- voice typing ----
        section("Voice typing · বাংলা + English")
        val vcard = card()

        // premium-style inner panel: small uppercase labels over rounded, stroked input boxes
        fun vlabel(t: String) = text(t.uppercase(), 11f, muted, bold = true).apply {
            letterSpacing = 0.06f
            setPadding(dp(2), dp(14), 0, dp(6))
        }

        fun fieldBox(hint: String, filled: String): EditText = EditText(this).apply {
            setText(filled)
            this.hint = hint
            setTextColor(fg)
            setHintTextColor(muted)
            textSize = 14f
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), line)
            }
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = true
        }

        fun pillButton(labelText: String, bgColor: Int, fgColor: Int, bold: Boolean = false) = TextView(this).apply {
            this.text = labelText
            textSize = 14f
            setTextColor(fgColor)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(16), dp(13), dp(16), dp(13))
        }

        val vinner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(6), dp(14), dp(14))
        }
        vcard.addView(vinner)

        vinner.addView(vlabel("Gemini API key"))
        apiKeyEdit = fieldBox("paste your free Gemini API key (AIza…)", Prefs.getString(this, "gemini_key")).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        var keyShown = false
        val keyToggle = text("Show", 12f, accent, bold = true).apply {
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener {
                keyShown = !keyShown
                val sel = apiKeyEdit.selectionStart.coerceAtLeast(0)
                apiKeyEdit.inputType = if (keyShown) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                apiKeyEdit.setSelection(sel.coerceAtMost(apiKeyEdit.text.length))
                text = if (keyShown) "Hide" else "Show"
            }
        }
        vinner.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(apiKeyEdit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(keyToggle)
            },
        )

        vinner.addView(vlabel("Model"))
        modelEdit = fieldBox("fetch the list, or type e.g. gemini-2.5-flash", Prefs.getString(this, "gemini_model"))
        vinner.addView(modelEdit)

        val fetchBtn = pillButton("↓  Fetch model list from Google", line, accent, bold = true)
        vinner.addView(fetchBtn, lp(10))

        val saveBtn = pillButton("Save & Test", accent, onAccent, bold = true)
        vinner.addView(saveBtn, lp(10))

        val savedModel = Prefs.getString(this, "gemini_model").trim()
        val savedKey = Prefs.getString(this, "gemini_key").trim()
        val voiceStatus = text(
            if (savedKey.isNotEmpty() && savedModel.isNotEmpty()) "Configured · $savedModel" else "",
            13f,
            if (savedKey.isNotEmpty() && savedModel.isNotEmpty()) accent else muted,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(4))
        }
        vinner.addView(voiceStatus)

        val errColor = Color.parseColor("#E53935")

        fun saveAndTest() {
            val key = apiKeyEdit.text.toString().trim()
            val model = modelEdit.text.toString().trim().removePrefix("models/")
            if (model != modelEdit.text.toString()) modelEdit.setText(model)
            if (key.isEmpty()) {
                Toast.makeText(this@SettingsActivity, "Paste your Gemini API key first", Toast.LENGTH_SHORT).show()
                return
            }
            if (model.isEmpty()) {
                Toast.makeText(this@SettingsActivity, "Fetch the model list or type a model name", Toast.LENGTH_SHORT).show()
                return
            }
            Prefs.setString(this@SettingsActivity, "gemini_key", key)
            Prefs.setString(this@SettingsActivity, "gemini_model", model)
            saveBtn.isEnabled = false
            fetchBtn.isEnabled = false
            saveBtn.text = "Testing…"
            voiceStatus.text = "Testing API key & $model…"
            voiceStatus.setTextColor(muted)
            Thread {
                // step 1: the key alone - GET /models cannot fail for model reasons
                val cat = GeminiVoice.fetchModels(key)
                if (cat is GeminiVoice.ModelList.Fail) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        saveBtn.isEnabled = true
                        fetchBtn.isEnabled = true
                        saveBtn.text = "Save & Test"
                        voiceStatus.text = cat.message
                        voiceStatus.setTextColor(errColor)
                    }
                    return@Thread
                }
                val models = (cat as GeminiVoice.ModelList.Ok).models
                if (models.isNotEmpty()) {
                    // refresh the fallback catalogue while we are here
                    Prefs.setString(this@SettingsActivity, "models_cache", models.joinToString("\n"))
                }
                // step 2: the chosen model, with a minimal request
                val err = GeminiVoice.ping(key, model)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    saveBtn.isEnabled = true
                    fetchBtn.isEnabled = true
                    saveBtn.text = "Save & Test"
                    if (err == null) {
                        voiceStatus.text = "Saved ✓  ·  $model is working"
                        voiceStatus.setTextColor(accent)
                    } else {
                        voiceStatus.text = "Key is OK ✓ — but $model failed: $err"
                        voiceStatus.setTextColor(errColor)
                    }
                }
            }.start()
        }

        fun fetchModels() {
            val key = apiKeyEdit.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this@SettingsActivity, "Paste your Gemini API key first", Toast.LENGTH_SHORT).show()
                return
            }
            Prefs.setString(this@SettingsActivity, "gemini_key", key)
            fetchBtn.text = "Fetching models…"
            fetchBtn.isEnabled = false
            saveBtn.isEnabled = false
            Thread {
                val res = GeminiVoice.fetchModels(key)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    fetchBtn.text = "↓  Fetch model list from Google"
                    fetchBtn.isEnabled = true
                    saveBtn.isEnabled = true
                    when (res) {
                        is GeminiVoice.ModelList.Ok -> {
                            if (res.models.isEmpty()) {
                                Toast.makeText(this@SettingsActivity, "No text models found for this key", Toast.LENGTH_LONG).show()
                            } else {
                                // remember the catalogue so voice typing can fall back to another model
                                Prefs.setString(this@SettingsActivity, "models_cache", res.models.joinToString("\n"))
                                AlertDialog.Builder(this)
                                    .setTitle("Pick a Gemini model (${res.models.size})")
                                    .setItems(res.models.toTypedArray()) { _, which ->
                                        modelEdit.setText(res.models[which])
                                        saveAndTest()
                                    }
                                    .setNeutralButton("Close", null)
                                    .show()
                            }
                        }
                        is GeminiVoice.ModelList.Fail -> {
                            voiceStatus.text = res.message
                            voiceStatus.setTextColor(errColor)
                        }
                    }
                }
            }.start()
        }

        fetchBtn.setOnClickListener { fetchModels() }
        saveBtn.setOnClickListener { saveAndTest() }

        fun segRow(labelTitle: String, prefKey: String, defVal: String, options: List<Pair<String, String>>) {
            vinner.addView(vlabel(labelTitle))
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = rounded(bg, 12)
                setPadding(dp(3), dp(3), dp(3), dp(3))
            }
            fun redraw() {
                bar.removeAllViews()
                val curVal = Prefs.getString(this@SettingsActivity, prefKey, defVal).ifEmpty { defVal }
                for ((id, name) in options) {
                    val on = curVal == id
                    bar.addView(
                        text(name, 12f, if (on) onAccent else fg, bold = on).apply {
                            gravity = Gravity.CENTER
                            background = if (on) rounded(accent, 9) else null
                            setOnClickListener {
                                Prefs.setString(this@SettingsActivity, prefKey, id)
                                redraw()
                            }
                        },
                        LinearLayout.LayoutParams(0, dp(36), 1f),
                    )
                }
            }
            redraw()
            vinner.addView(bar)
        }
        segRow("Voice language", "voice_lang", "auto", listOf("auto" to "Auto (বাং+En)", "bn" to "বাংলা", "en" to "English"))
        segRow("Auto-stop silence", "voice_silence", "normal", listOf("fast" to "Fast 1.2s", "normal" to "Normal 1.8s", "relaxed" to "Relaxed 2.8s"))

        row(vcard, true, "Get a free API key (aistudio.google.com)", chevron()) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }
        toggleRow(vcard, false, "Show mic button", "voice")
        toggleRow(vcard, false, "Auto-punctuation & capitalisation", "voice_punct")
        toggleRow(vcard, false, "Voice haptic feedback", "voice_haptic")
        root.addView(
            text(
                "Tap the mic at the top-right of the keyboard, speak in Bangla or English, then tap again (or just pause) " +
                    "to stop - the words land at the cursor. Audio goes to Google's Gemini API with your own free key; " +
                    "nothing else is sent anywhere. The first time, Android asks for the microphone permission.",
                12f, muted,
            ),
            lp(6),
        )

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(bg)
                isVerticalScrollBarEnabled = false
                addView(root)
            },
        )
    }

    /** Recent reasons Android ended the keyboard process (API 30+), plus the last crash the app itself caught. */
    private fun diagnostics(): String {
        val sb = StringBuilder()
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
                val list = am.getHistoricalProcessExitReasons(packageName, 0, 8)
                if (list.isEmpty()) sb.append("No process exits recorded.\n")
                for (r in list) {
                    sb.append(fmt.format(Date(r.timestamp))).append("  ").append(reasonName(r.reason))
                    if (!r.description.isNullOrEmpty()) sb.append(" (").append(r.description).append(")")
                    sb.append("\n")
                }
            } catch (e: Exception) {
                sb.append("Exit history unavailable.\n")
            }
        } else {
            sb.append("Exit history needs Android 11 or newer.\n")
        }
        val last = CrashLog.last(this)
        sb.append("\nLast error caught:\n").append(if (last.isEmpty()) "none" else last)
        return sb.toString()
    }

    private fun reasonName(r: Int): String = if (Build.VERSION.SDK_INT < 30) "unknown" else when (r) {
        ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE -> "Crash"
        ApplicationExitInfo.REASON_ANR -> "Not responding (ANR)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Killed: low memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Killed: excessive resource use"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "Stopped by user (force stop / swipe)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "Stopped by user"
        ApplicationExitInfo.REASON_SIGNALED -> "Killed by system / battery manager"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Dependency died"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "Permission changed"
        ApplicationExitInfo.REASON_OTHER -> "Other"
        ApplicationExitInfo.REASON_EXIT_SELF -> "Exited itself"
        else -> "Reason $r"
    }

    private fun refreshSwatches(fg: Int, muted: Int) {
        swatchRow.removeAllViews()
        val selectedId = Prefs.theme(this)
        for (id in Themes.ids(this)) {
            val t = Themes.resolve(this, id)
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, 0, dp(10), 0)
            }
            cell.addView(Swatch(this, t, id == selectedId), LinearLayout.LayoutParams(dp(84), dp(56)))
            cell.addView(
                TextView(this).apply {
                    text = Themes.label(id)
                    textSize = 12f
                    setTextColor(if (id == selectedId) fg else muted)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, 0)
                },
            )
            cell.setOnClickListener {
                Prefs.setTheme(this, id)
                refreshSwatches(fg, muted)
                OpenRidmikService.live?.reloadFromPrefs()
            }
            swatchRow.addView(cell)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            901 -> {
                try {
                    val snips = org.json.JSONObject()
                    for ((k, v) in Snippets.all(this)) snips.put(k, v)
                    val pinned = org.json.JSONArray()
                    for (item in ClipHistory.ordered(this)) if (item.pinned) pinned.put(item.text)
                    val rootObj = org.json.JSONObject()
                        .put("userdict", UserDict.exportJson(this))
                        .put("snippets", snips)
                        .put("pinned_clips", pinned)
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(rootObj.toString(2).toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "Backup exported ✓", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            902 -> {
                try {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                    val rootObj = org.json.JSONObject(raw)
                    rootObj.optJSONObject("userdict")?.let { UserDict.importJson(this, it) }
                    rootObj.optJSONObject("snippets")?.let { s ->
                        val keys = s.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            Snippets.add(this, k, s.optString(k))
                        }
                    }
                    rootObj.optJSONArray("pinned_clips")?.let { arr ->
                        val list = ArrayList<String>(arr.length())
                        for (i in 0 until arr.length()) list.add(arr.optString(i))
                        ClipHistory.importPinned(this, list)
                    }
                    Toast.makeText(this, "Backup restored ✓", Toast.LENGTH_SHORT).show()
                    recreate()
                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid backup file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ---- in-app update (GitHub releases) ----

    /** Tag + APK download URL of the newest GitHub release, or null on any network/parse problem. */
    private fun fetchLatestRelease(): Pair<String, String>? {
        val conn = URL("https://api.github.com/repos/khad3m/ridmik-pro/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "RidmikPro-Updater")
        try {
            val obj = org.json.JSONObject(conn.inputStream.bufferedReader().readText())
            val assets = obj.getJSONArray("assets")
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url") ?: return null
            return obj.getString("tag_name") to apkUrl
        } finally {
            conn.disconnect()
        }
    }

    /** Numeric comparison of dotted versions ("v1.2.6" vs "1.2.5"); missing parts count as 0. */
    private fun isNewerVersion(remote: String, local: String): Boolean {
        fun parts(v: String) = v.trim().trimStart('v', 'V')
            .split(".").map { s -> s.takeWhile { it.isDigit() }.ifEmpty { "0" }.toInt() }
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /** Streams the release APK into [dest], reporting progress in whole percent. */
    private fun downloadApk(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "RidmikPro-Updater")
        try {
            val total = conn.contentLengthLong
            dest.outputStream().use { out ->
                conn.inputStream.use { ins ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPct = -20
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = (read * 100 / total).toInt()
                            if (pct >= lastPct + 10) {
                                lastPct = pct
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Hands a downloaded APK to the system installer via a PackageInstaller session. */
    private fun installApk(apk: File) {
        val pi = packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pi.createSession(params)
        val session = pi.openSession(sessionId)
        try {
            session.openWrite("RidmikPro.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { ins -> ins.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            // status broadcast has no receiver on purpose: the system install dialog is the feedback
            val sender = PendingIntent.getBroadcast(
                this,
                sessionId,
                Intent("com.ridmik.pro.INSTALL_RESULT").setPackage(packageName),
                flags,
            )
            session.commit(sender.intentSender)
        } catch (e: Exception) {
            session.abandon()
            throw e
        } finally {
            session.close()
        }
    }
}
