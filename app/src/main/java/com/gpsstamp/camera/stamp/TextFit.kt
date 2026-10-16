package com.gpsstamp.camera.stamp

private const val ELLIPSIS = "…"

/** Fits text into a pixel width; [measure] returns a string's drawn width (Paint::measureText on device). */
object TextFit {
    fun ellipsize(text: String, maxWidth: Float, measure: (String) -> Float): String {
        if (measure(text) <= maxWidth) return text
        for (end in text.length - 1 downTo 1) {
            val candidate = text.substring(0, end).trimEnd() + ELLIPSIS
            if (measure(candidate) <= maxWidth) return candidate
        }
        return ELLIPSIS
    }

    /** Greedy word wrap; when text needs more than [maxLines], the last line carries the rest, ellipsized. */
    fun wrap(text: String, maxWidth: Float, maxLines: Int, measure: (String) -> Float): List<String> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var current = ""
        var i = 0
        while (i < words.size) {
            val candidate = if (current.isEmpty()) words[i] else "$current ${words[i]}"
            if (measure(candidate) <= maxWidth) {
                current = candidate
                i++
                continue
            }
            if (lines.size == maxLines - 1) {
                val rest = (listOf(current) + words.drop(i)).filter { it.isNotEmpty() }.joinToString(" ")
                lines += ellipsize(rest, maxWidth, measure)
                return lines
            }
            if (current.isEmpty()) {
                lines += ellipsize(words[i], maxWidth, measure)
                i++
            } else {
                lines += current
                current = ""
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}
