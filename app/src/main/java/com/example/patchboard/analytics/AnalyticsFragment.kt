package com.example.patchboard.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.patchboard.R
import com.example.patchboard.databinding.FragmentAnalyticsBinding
import com.example.patchboard.databinding.ItemLegendRowBinding
import com.example.patchboard.databinding.ItemLocationStatBinding
import com.example.patchboard.databinding.ItemPriorityStatBinding
import com.example.patchboard.model.Priority
import com.example.patchboard.model.Stage
import com.example.patchboard.ui.BoardViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "Reports" page: charts and per-location insights for facility administrators.
 *
 * It only talks to a [ReportSource]. To move to workspaces, change [createSource] (one line)
 * and pass a workspace id through [newInstance].
 */
class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!

    private lateinit var source: ReportSource
    private var scope = AnalyticsScope.ALL
    private var period = Period.MONTH

    /** The single place that decides where reports come from. */
    private fun createSource(): ReportSource {
        val board = ViewModelProvider(requireActivity())[BoardViewModel::class.java]
        return BoardReportSource(board)
        // TODO(workspaces): return WorkspaceReportSource(repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scope = AnalyticsScope(arguments?.getString(ARG_WORKSPACE_ID))
        period = savedInstanceState?.getString(STATE_PERIOD)
            ?.let { name -> Period.values().firstOrNull { it.name == name } }
            ?: Period.MONTH
        source = createSource()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Keep the header below the status bar.
        val headerTop = binding.header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, headerTop + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(binding.header)

        binding.periodGroup.check(chipFor(period))
        binding.periodGroup.setOnCheckedStateChangeListener { _, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            period = periodFor(id)
            render()
        }

        // Re-renders whenever the reports change (e.g. an admin moves a card to Fixed).
        source.observe(viewLifecycleOwner) { if (_binding != null) render() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PERIOD, period.name)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        val b = binding
        val summary = AnalyticsEngine.compute(source.records(scope), period)

        b.tvKpiTotal.text = summary.total.toString()
        b.tvKpiOpen.text = summary.open.toString()
        b.tvKpiFixed.text = summary.fixed.toString()
        b.tvKpiRate.text = summary.fixRatePercent
            ?.let { getString(R.string.kpi_rate_value, it) }
            ?: getString(R.string.kpi_rate_none)

        val hasData = summary.total > 0
        b.emptyState.isVisible = !hasData
        b.contentGroup.isVisible = hasData
        if (!hasData) return

        renderInsights(summary)
        renderTrend(summary)
        renderStatus(summary)
        renderPriority(summary)
        renderLocations(summary)
    }

    private fun renderInsights(s: AnalyticsSummary) {
        val b = binding

        val hot = s.hotspot
        b.tvInsightHotspot.text = if (hot == null) {
            ""
        } else {
            getString(
                R.string.insight_hotspot,
                hot.location.tag,
                hot.total,
                hot.sharePercent,
                getString(periodPhrase(s.period)),
            )
        }

        val attention = s.needsAttention
        b.tvInsightAttention.text = if (attention == null) {
            getString(R.string.insight_attention_none)
        } else {
            getString(R.string.insight_attention, attention.location.tag, attention.openHigh)
        }

        b.tvInsightSpeed.text = s.averageFixMillis
            ?.let { getString(R.string.insight_speed, formatDuration(it)) }
            ?: getString(R.string.insight_speed_none)
    }

    private fun renderTrend(s: AnalyticsSummary) {
        val pattern = if (s.period == Period.WEEK) "EEE" else "d MMM"
        val fmt = SimpleDateFormat(pattern, Locale.getDefault())
        val labels = s.trend.map { fmt.format(Date(it.startMillis)) }
        binding.trendChart.setData(s.trend, labels)
        binding.trendChart.contentDescription = getString(R.string.cd_trend_chart)
    }

    private fun renderStatus(s: AnalyticsSummary) {
        val b = binding
        val rows = listOf(
            Triple(Stage.REPORTED, R.string.status_reported, R.color.chart_status_reported),
            Triple(Stage.IN_PROGRESS, R.string.status_in_progress, R.color.chart_status_in_progress),
            Triple(Stage.FIXED, R.string.status_fixed, R.color.chart_status_fixed),
        )
        val ctx = requireContext()

        b.donut.setData(
            slices = rows.map { (stage, _, color) ->
                DonutChartView.Slice(s.stageCounts[stage] ?: 0, ContextCompat.getColor(ctx, color))
            },
            centerValue = s.total.toString(),
            centerLabel = getString(R.string.donut_center_label),
        )
        b.donut.contentDescription = getString(
            R.string.cd_status_chart,
            s.stageCounts[Stage.REPORTED] ?: 0,
            s.stageCounts[Stage.IN_PROGRESS] ?: 0,
            s.stageCounts[Stage.FIXED] ?: 0,
        )

        b.statusLegend.removeAllViews()
        rows.forEach { (stage, label, color) ->
            val row = ItemLegendRowBinding.inflate(layoutInflater, b.statusLegend, false)
            row.dot.backgroundTintList = ContextCompat.getColorStateList(ctx, color)
            row.tvLabel.setText(label)
            row.tvValue.text = (s.stageCounts[stage] ?: 0).toString()
            b.statusLegend.addView(row.root)
        }
    }

    private fun renderPriority(s: AnalyticsSummary) {
        val b = binding
        val ctx = requireContext()
        val max = s.priorityCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1

        b.priorityList.removeAllViews()
        // High first so the most urgent mix reads at the top.
        listOf(Priority.HIGH, Priority.MEDIUM, Priority.LOW).forEach { p ->
            val count = s.priorityCounts[p] ?: 0
            val row = ItemPriorityStatBinding.inflate(layoutInflater, b.priorityList, false)
            row.tvPriority.text = p.label
            pill(row.tvPriority, p.bgRes, p.textRes)
            row.tvCount.text = resources.getQuantityString(R.plurals.reports_count, count, count)
            row.progress.setIndicatorColor(ContextCompat.getColor(ctx, p.dotRes))
            row.progress.setProgressCompat(count * 100 / max, false)
            b.priorityList.addView(row.root)
        }
    }

    private fun renderLocations(s: AnalyticsSummary) {
        val b = binding
        val ctx = requireContext()
        val max = s.locations.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1

        b.locationList.removeAllViews()
        s.locations.forEach { loc ->
            val row = ItemLocationStatBinding.inflate(layoutInflater, b.locationList, false)
            row.tvTag.text = loc.location.tag
            pill(row.tvTag, loc.location.bgRes, loc.location.textRes)
            row.tvDetail.text = getString(
                R.string.location_row_detail,
                resources.getQuantityString(R.plurals.reports_count, loc.total, loc.total),
                loc.open,
            )
            row.progress.setProgressCompat(loc.total * 100 / max, false)
            b.locationList.addView(row.root)
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Rounded colored badge; the model's bgRes/textRes are color resources. */
    private fun pill(view: android.widget.TextView, bgColorRes: Int, textColorRes: Int) {
        val ctx = requireContext()
        view.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(ContextCompat.getColor(ctx, bgColorRes))
            cornerRadius = 100f * resources.displayMetrics.density
        }
        view.setTextColor(ContextCompat.getColor(ctx, textColorRes))
    }

    private fun formatDuration(millis: Long): String {
        val hours = millis / 3_600_000L
        return when {
            hours < 1 -> getString(R.string.duration_under_hour)
            hours < 48 -> resources.getQuantityString(R.plurals.duration_hours, hours.toInt(), hours.toInt())
            else -> getString(R.string.duration_days, millis / 86_400_000.0)
        }
    }

    private fun periodPhrase(p: Period): Int = when (p) {
        Period.WEEK -> R.string.period_phrase_7d
        Period.MONTH -> R.string.period_phrase_30d
        Period.QUARTER -> R.string.period_phrase_90d
        Period.ALL -> R.string.period_phrase_all
    }

    private fun chipFor(p: Period): Int = when (p) {
        Period.WEEK -> R.id.chipWeek
        Period.MONTH -> R.id.chipMonth
        Period.QUARTER -> R.id.chipQuarter
        Period.ALL -> R.id.chipAll
    }

    private fun periodFor(chipId: Int): Period = when (chipId) {
        R.id.chipWeek -> Period.WEEK
        R.id.chipQuarter -> Period.QUARTER
        R.id.chipAll -> Period.ALL
        else -> Period.MONTH
    }

    companion object {
        private const val ARG_WORKSPACE_ID = "workspace_id"
        private const val STATE_PERIOD = "period"

        /** Pass a workspace id once workspaces exist; null shows everything. */
        fun newInstance(workspaceId: String? = null) = AnalyticsFragment().apply {
            arguments = Bundle().apply { putString(ARG_WORKSPACE_ID, workspaceId) }
        }
    }
}
