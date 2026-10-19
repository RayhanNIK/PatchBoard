package com.example.patchboard.model

object LocationTagNames {
    const val MIN_LENGTH = 2          // letters/digits, without the '#'
    const val MAX_LENGTH = 24

    fun normalize(raw: String): String? {
        val words = raw.trim().trimStart('#').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        if (words.any { w -> !w.all { it.isLetterOrDigit() } }) return null
        val body = words.joinToString("") { w -> w.replaceFirstChar { it.uppercaseChar() } }
        if (body.length < MIN_LENGTH || body.length > MAX_LENGTH) return null
        return "#$body"
    }
    fun idFor(tag: String): String = tag.removePrefix("#").lowercase()
}
