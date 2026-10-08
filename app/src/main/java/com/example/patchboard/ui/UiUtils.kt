package com.example.patchboard.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.patchboard.R
import com.example.patchboard.model.LocationTag
import com.example.patchboard.model.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.color(@ColorRes res: Int): Int = ContextCompat.getColor(this, res)

fun View.tintBackground(color: Int) {
    backgroundTintList = ColorStateList.valueOf(color)
}

// ── Avatars ──────────────────────────────────────────────────────────────────
private val AVATAR_COLORS = intArrayOf(
    0xFF6750A4.toInt(), 0xFF7D5260.toInt(), 0xFF006D36.toInt(), 0xFFB3261E.toInt(),
    0xFF0D47A1.toInt(), 0xFF4A148C.toInt(), 0xFF9C4300.toInt(),
)

fun avatarColor(name: String): Int {
    var h = 0
    for (ch in name) h = ch.code + ((h shl 5) - h)
    return AVATAR_COLORS[abs(h % AVATAR_COLORS.size)]
}

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (parts.size >= 2) "${parts.first()[0]}${parts.last()[0]}".uppercase()
    else name.trim().take(2).uppercase()
}

/** Turns a plain TextView into a circular initials avatar of [sizeDp]. */
fun TextView.bindAvatar(name: String, sizeDp: Int) {
    val px = context.dp(sizeDp)
    layoutParams = layoutParams.apply { width = px; height = px }
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(avatarColor(name)) }
    setTextColor(Color.WHITE)
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, sizeDp * 0.38f)
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    gravity = Gravity.CENTER
    text = initials(name)
}

// ── Time ─────────────────────────────────────────────────────────────────────
fun relativeTime(createdAt: Long, now: Long = System.currentTimeMillis()): String {
    val hours = max(0.0, (now - createdAt) / 3_600_000.0)
    if (hours < 24) return "${floor(hours).toInt()}h ago"
    val days = hours / 24
    if (days < 7) return "${floor(days).toInt()}d ago"
    if (days < 30) return "${floor(days / 7).toInt()}w ago"
    if (days < 365) return "${floor(days / 30).toInt()}mo ago"
    return "${floor(days / 365).toInt()}y ago"
}

fun fullDateTime(context: Context, createdAt: Long): String {
    val date = Date(createdAt)
    val day = SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
    return "$day · ${DateFormat.getTimeFormat(context).format(date)}"
}

/** Shows "3h ago"; tap toggles to the full date/time (replaces the hover behaviour of the web version). */
fun TextView.bindCreatedTime(createdAt: Long) {
    val full = fullDateTime(context, createdAt)
    var expanded = false
    text = relativeTime(createdAt)
    contentDescription = "Created $full"
    setOnClickListener {
        expanded = !expanded
        text = if (expanded) full else relativeTime(createdAt)
    }
}

// ── Chips ────────────────────────────────────────────────────────────────────
fun TextView.bindLocationTag(location: LocationTag) {
    text = location.tag
    setBackgroundResource(R.drawable.bg_location_tag)
    tintBackground(context.color(location.bgRes))
    setTextColor(context.color(location.textRes))
}

fun TextView.bindPriority(priority: Priority) {
    text = priority.label
    setBackgroundResource(R.drawable.bg_priority_chip)
    tintBackground(context.color(priority.bgRes))
    setTextColor(context.color(priority.textRes))
    val dot = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(context.color(priority.dotRes))
        setSize(context.dp(6), context.dp(6))
    }
    setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
    compoundDrawablePadding = context.dp(5)
}
