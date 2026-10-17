package com.example.patchboard.model

import androidx.annotation.ColorRes
import com.example.patchboard.R

enum class Priority(
    val label: String,
    @ColorRes val bgRes: Int,
    @ColorRes val textRes: Int,
    @ColorRes val dotRes: Int,
) {
    HIGH("High", R.color.prio_high_bg, R.color.prio_high_text, R.color.prio_high_dot),
    MEDIUM("Medium", R.color.prio_medium_bg, R.color.prio_medium_text, R.color.prio_medium_dot),
    LOW("Low", R.color.prio_low_bg, R.color.prio_low_text, R.color.prio_low_dot),
}

enum class LocationTag(
    val tag: String,
    @ColorRes val bgRes: Int,
    @ColorRes val textRes: Int,
) {
    IT_LAB("#ITLab", R.color.loc_itlab_bg, R.color.loc_itlab_text),
    LIBRARY("#Library", R.color.loc_library_bg, R.color.loc_library_text),
    CAFETERIA("#Cafeteria", R.color.loc_cafeteria_bg, R.color.loc_cafeteria_text),
    GYM("#Gym", R.color.loc_gym_bg, R.color.loc_gym_text),
    LECTURE_HALL("#LectureHall", R.color.loc_lecture_bg, R.color.loc_lecture_text),
    RESTROOM("#Restroom", R.color.loc_restroom_bg, R.color.loc_restroom_text),
}

/** Board stages, in workflow order. */
enum class Stage(val title: String, @ColorRes val accentRes: Int) {
    REPORTED("1. Reported", R.color.stage_reported),
    IN_PROGRESS("2. In Progress", R.color.stage_inprogress),
    FIXED("3. Fixed", R.color.stage_fixed),
}

data class Comment(
    val id: String,
    val author: String,
    val text: String,
    val createdAt: Long,          // epoch millis
    val isEdited: Boolean = false,
    val authorId: String? = null,   // account id of the writer (null = seed/demo data)
    val byAdmin: Boolean = false,   // written while the poster was in admin mode
)

data class ReportCard(
    val id: Int,
    val location: LocationTag,
    val title: String,
    val description: String,
    val priority: Priority,
    val upvotes: Int,
    val comments: List<Comment>,
    val createdAt: Long,          // epoch millis
    val imageUri: String? = null, // content:// or file:// URI string
    val resolvedAt: Long? = null,
    val lat: Double? = null,      // optional map pin
    val lng: Double? = null,
    val authorId: String? = null,   // account id of the reporter (null = seed/demo data)
    val authorName: String? = null, // shown on the card as "by <name>"
    val upvotedBy: Set<String> = emptySet(),   // account ids that already upvoted
    val upvotedByMe: Boolean = false,          // filled in per viewer by the ViewModel
) {
    val hasPin: Boolean get() = lat != null && lng != null
}

data class Column(val stage: Stage, val cards: List<ReportCard>)

enum class SortMode { OFF, DESC, ASC }
