package com.example.patchboard.analytics

import androidx.lifecycle.LifecycleOwner
import com.example.patchboard.model.ReportCard
import com.example.patchboard.model.Stage
import com.example.patchboard.ui.BoardViewModel

/** Which slice of the data an analytics page shows. Workspaces plug in here later. */
data class AnalyticsScope(
    /** null = everything (the only option until workspaces exist). */
    val workspaceId: String? = null,
) {
    companion object {
        val ALL = AnalyticsScope()
    }
}

/**
 * Where analytics gets its reports from.
 *
 * TODO(workspaces): add a `WorkspaceReportSource` that loads reports for `scope.workspaceId`
 * from your workspace repository / backend, then change the one line in AnalyticsFragment
 * that creates the source. Nothing else in the analytics code needs to change.
 */
interface ReportSource {
    fun records(scope: AnalyticsScope): List<ReportRecord>

    /** Calls [onChange] right away and again whenever the reports change, until [owner] is destroyed. */
    fun observe(owner: LifecycleOwner, onChange: () -> Unit)
}

/** Today's source: the in-memory board that the Board tab edits. */
class BoardReportSource(private val board: BoardViewModel) : ReportSource {

    override fun records(scope: AnalyticsScope): List<ReportRecord> {
        val now = System.currentTimeMillis()
        // The single built-in workspace has workspaceId == null, so any scope sees all of it for now.
        return board.snapshot().flatMap { column ->
            column.cards.map { card -> card.toRecord(column.stage, now) }
        }
    }

    override fun observe(owner: LifecycleOwner, onChange: () -> Unit) = board.observe(owner, onChange)
}

private const val DAY_MS = 86_400_000L

private fun ReportCard.toRecord(stage: Stage, now: Long): ReportRecord {
    val resolved = if (stage != Stage.FIXED) {
        null
    } else {
        // Real value once an admin moves a card to Fixed. The demo data was created already-fixed,
        // so give it a stable 1-6 day repair time instead of leaving it empty.
        resolvedAt ?: minOf(now, createdAt + (1 + id % 6) * DAY_MS)
    }
    return ReportRecord(
        id = id,
        workspaceId = null,
        location = location,
        priority = priority,
        stage = stage,
        createdAt = createdAt,
        resolvedAt = resolved,
        upvotes = upvotes,
        commentCount = comments.size,
    )
}
