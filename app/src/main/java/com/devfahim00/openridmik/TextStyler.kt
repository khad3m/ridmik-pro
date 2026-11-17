package com.devfahim00.openridmik

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zero-asset Unicode stylish font mapper + social/WhatsApp/FB text formatters.
 * Works directly in any app (Facebook, WhatsApp, Messenger, Instagram, etc.).
 */
object TextStyler {

    enum class Font(val label: String) {
        NORMAL("Normal"),
        BOLD("𝐁𝐨𝐥𝐝"),
        ITALIC("𝐼𝑡𝑎𝑙𝑖𝑐"),
        BOLD_ITALIC("𝑩𝒐𝒍𝒅 𝑰𝒕"),
        MONO("𝙼𝚘𝚗𝚘"),
        SCRIPT("𝒮𝒸𝓇𝒾𝓅𝓉"),
        DOUBLE("𝔻𝕠𝕦𝕓𝕝𝕖"),
        BUBBLE("Ⓑⓤⓑⓑⓛⓔ"),
        SMALL_CAPS("ꜱᴍᴀʟʟ"),
    }

    private val SMALL_CAPS_MAP = mapOf(
        'a' to "ᴀ", 'b' to "ʙ", 'c' to "ᴄ", 'd' to "ᴅ", 'e' to "ᴇ", 'f' to "ꜰ", 'g' to "ɢ",
        'h' to "ʜ", 'i' to "ɪ", 'j' to "ᴊ", 'k' to "ᴋ", 'l' to "ʟ", 'm' to "ᴍ", 'n' to "ɴ",
        'o' to "ᴏ", 'p' to "ᴘ", 'q' to "ǫ", 'r' to "ʀ", 's' to "ꜱ", 't' to "ᴛ", 'u' to "ᴜ",
        'v' to "ᴠ", 'w' to "ᴡ", 'x' to "x", 'y' to "ʏ", 'z' to "ᴢ",
    )

    private val DOUBLE_EXC = mapOf(
        'C' to 0x2102, 'H' to 0x210D, 'N' to 0x2115, 'P' to 0x2119,
        'Q' to 0x211A, 'R' to 0x211D, 'Z' to 0x2124,
    )

    private val SCRIPT_EXC = mapOf(
        'B' to 0x212C, 'E' to 0x2130, 'F' to 0x2131, 'H' to 0x210B,
        'I' to 0x2110, 'L' to 0x2112, 'M' to 0x2133, 'R' to 0x211B,
        'e' to 0x212F, 'g' to 0x210A, 'o' to 0x2134,
    )

    fun style(text: String, font: Font): String {
        if (font == Font.NORMAL || text.isEmpty()) return text
        val sb = StringBuilder(text.length * 2)
        for (ch in text) {
            when (font) {
                Font.NORMAL -> sb.append(ch)
                Font.BOLD -> sb.appendCodePoint(mapRange(ch, 0x1D400, 0x1D41A, 0x1D7CE))
                Font.ITALIC -> {
                    if (ch == 'h') sb.appendCodePoint(0x210E)
                    else sb.appendCodePoint(mapRange(ch, 0x1D434, 0x1D44E, -1))
                }
                Font.BOLD_ITALIC -> sb.appendCodePoint(mapRange(ch, 0x1D468, 0x1D482, -1))
                Font.MONO -> sb.appendCodePoint(mapRange(ch, 0x1D670, 0x1D68A, 0x1D7F6))
                Font.SCRIPT -> {
                    val exc = SCRIPT_EXC[ch]
                    if (exc != null) sb.appendCodePoint(exc)
                    else sb.appendCodePoint(mapRange(ch, 0x1D49C, 0x1D4B6, -1))
                }
                Font.DOUBLE -> {
                    val exc = DOUBLE_EXC[ch]
                    if (exc != null) sb.appendCodePoint(exc)
                    else sb.appendCodePoint(mapRange(ch, 0x1D538, 0x1D552, 0x1D7D8))
                }
                Font.BUBBLE -> {
                    when (ch) {
                        in 'A'..'Z' -> sb.appendCodePoint(0x24B6 + (ch - 'A'))
                        in 'a'..'z' -> sb.appendCodePoint(0x24D0 + (ch - 'a'))
                        '0' -> sb.appendCodePoint(0x24EA)
                        in '1'..'9' -> sb.appendCodePoint(0x2460 + (ch - '1'))
                        else -> sb.append(ch)
                    }
                }
                Font.SMALL_CAPS -> {
                    val mapped = SMALL_CAPS_MAP[ch.lowercaseChar()]
                    if (mapped != null && ch in 'a'..'z') sb.append(mapped) else sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private fun mapRange(ch: Char, upperBase: Int, lowerBase: Int, digitBase: Int): Int = when (ch) {
        in 'A'..'Z' -> upperBase + (ch - 'A')
        in 'a'..'z' -> lowerBase + (ch - 'a')
        in '0'..'9' -> if (digitBase >= 0) digitBase + (ch - '0') else ch.code
        else -> ch.code
    }

    fun toTitleCase(s: String): String = s.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { part ->
        if (part.isNotEmpty() && part[0].isLetter()) {
            part.lowercase().replaceFirstChar { it.uppercaseChar() }
        } else part
    }

    fun todayDate(): String = SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())

    fun nowTime(): String = SimpleDateFormat("hh:mm a", Locale.US).format(Date())
}
