package com.example.patchboard.analytics

import com.example.patchboard.model.LocationTag
import com.example.patchboard.model.Priority
import com.example.patchboard.model.Stage
import java.util.Calendar

/** Time ranges the page can show. [buckets] = bars on the "reports over time" chart. */
enum class Period(val days: Int, val buckets: Int) {
    WEEK(7, 7),
    MONTH(30, 6),
    QUARTER(90, 6),
    ALL(0, 8),
}

/** One bar group on the trend chart. */
data class TrendBucket(
    val startMillis: Long,
    /** Reports filed (created) in this slice of time. */
    val filed: Int,
    /** Repairs completed (moved to Fixed) in this slice of time. */
    val repaired: Int,
)

data class LocationStat(
    val location: LocationTag,
    val total: Int,
    val open: Int,
    val fixed: Int,
    /** Open reports that are High priority. */
    val openHigh: Int,
    val sharePercent: Int,
)

data class AnalyticsSummary(
    val period: Period,
    val windowStart: Long,
    val windowEnd: Long,
    /** Everything below except [trend]'s `repaired` is about reports FILED inside the window. */
    val total: Int,
    val open: Int,
    val fixed: Int,
    val fixRatePercent: Int?,
    val averageFixMillis: Long?,
    val stageCounts: Map<Stage, Int>,
    val priorityCounts: Map<Priority, Int>,
    /** Only locations that have reports, busiest first. */
    val locations: List<LocationStat>,
    val trend: List<TrendBucket>,
) {
    /** The location with the most reports. */
    val hotspot: LocationStat? get() = locations.firstOrNull()

    /** The location with the most open High-priority reports (null when there are none). */
    val needsAttention: LocationStat?
        get() = locations.filter { it.openHigh > 0 }
            .maxWithOrNull(compareBy<LocationStat> { it.openHigh }.thenBy { it.open })
}

/** Pure functions, no Android types: easy to unit test. */
object AnalyticsEngine {

    private const val DAY = 86_400_000L

    fun compute(
        records: List<ReportRecord>,
        period: Period,
        now: Long = System.currentTimeMillis(),
    ): AnalyticsSummary {
        val (start, end) = window(records, period, now)
        val span = end - start
        val n = period.buckets

        val filedInWindow = records.filter { it.createdAt in start until end }
        val total = filedInWindow.size
        val fixedList = filedInWindow.filter { it.stage == Stage.FIXED }
        val fixed = fixedList.size

        val durations = fixedList.mapNotNull { r -> r.resolvedAt?.let { (it - r.createdAt).coerceAtLeast(0L) } }
        val average = if (durations.isEmpty()) null else durations.sum() / durations.size

        val locations = LocationTag.values()
            .map { loc ->
                val rows = filedInWindow.filter { it.location == loc }
                LocationStat(
                    location = loc,
                    total = rows.size,
                    open = rows.count { !it.isFixed },
                    fixed = rows.count { it.isFixed },
                    openHigh = rows.count { !it.isFixed && it.priority == Priority.HIGH },
                    sharePercent = if (total == 0) 0 else Math.round(rows.size * 100.0 / total).toInt(),
                )
            }
            .filter { it.total > 0 }
            .sortedWith(
                compareByDescending<LocationStat> { it.total }
                    .thenByDescending { it.open }
                    .thenBy { it.location.ordinal }
            )

        // Trend: equal slices of [start, end).
        fun bucketOf(t: Long) = ((t - start) * n / span).toInt().coerceIn(0, n - 1)
        val filed = IntArray(n)
        val repaired = IntArray(n)
        filedInWindow.forEach { filed[bucketOf(it.createdAt)]++ }
        records.forEach { r ->
            val done = r.resolvedAt
            if (r.stage == Stage.FIXED && done != null && done in start until end) repaired[bucketOf(done)]++
        }
        val trend = (0 until n).map { i -> TrendBucket(start + span * i / n, filed[i], repaired[i]) }

        return AnalyticsSummary(
            period = period,
            windowStart = start,
            windowEnd = end,
            total = total,
            open = total - fixed,
            fixed = fixed,
            fixRatePercent = if (total == 0) null else Math.round(fixed * 100.0 / total).toInt(),
            averageFixMillis = average,
            stageCounts = Stage.values().associateWith { s -> filedInWindow.count { it.stage == s } },
            priorityCounts = Priority.values().associateWith { p -> filedInWindow.count { it.priority == p } },
            locations = locations,
            trend = trend,
        )
    }

    /** [start, end) of the period. WEEK is whole calendar days so the daily bars line up with midnight. */
    private fun window(records: List<ReportRecord>, period: Period, now: Long): Pair<Long, Long> =
        when (period) {
            Period.WEEK -> startOfDay(now, -6) to startOfDay(now, 1)
            Period.MONTH, Period.QUARTER -> (now - period.days * DAY) to (now + 1)
            Period.ALL -> {
                val oldest = records.minOfOrNull { it.createdAt }?.coerceAtMost(now) ?: (now - 30 * DAY)
                val end = now + 1
                // Never let the window collapse to a point (single brand-new report).
                minOf(oldest, end - DAY) to end
            }
        }

    private fun startOfDay(now: Long, plusDays: Int): Long = Calendar.getInstance().run {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, plusDays)
        timeInMillis
    }
}
