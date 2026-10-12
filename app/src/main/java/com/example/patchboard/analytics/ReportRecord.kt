package com.example.patchboard.analytics

import com.example.patchboard.model.LocationTag
import com.example.patchboard.model.Priority
import com.example.patchboard.model.Stage

/**
 * One report, flattened for analytics. The analytics code only ever sees this type, never
 * BoardViewModel / ReportCard, so the data can later come from a workspace backend instead.
 */
data class ReportRecord(
    val id: Int,
    /** Which workspace the report belongs to. null = the single built-in workspace (today). */
    val workspaceId: String?,
    val location: LocationTag,
    val priority: Priority,
    val stage: Stage,
    val createdAt: Long,
    /** When the report reached Fixed. null while it is not fixed. */
    val resolvedAt: Long?,
    val upvotes: Int,
    val commentCount: Int,
    /** For the Map tab. Both null when the reporter did not drop a pin. */
    val title: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
) {
    val hasPin: Boolean get() = lat != null && lng != null
    val isFixed: Boolean get() = stage == Stage.FIXED
}
