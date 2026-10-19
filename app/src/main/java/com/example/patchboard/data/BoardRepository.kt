package com.example.patchboard.data

/** Plain data copies of what is stored in Firestore. The rest of the app never sees Firebase types. */
data class ReportDoc(
    val id: String,
    val location: String,            // tag text, e.g. "#Library"
    val title: String,
    val description: String,
    val priority: String,            // Priority.name
    val stage: String,               // Stage.name
    val upvotes: Int,
    val upvotedBy: List<String>,
    val createdAt: Long,
    val orderAt: Long,               // newest-first ordering inside a column (bumped when a report moves)
    val resolvedAt: Long?,
    val lat: Double?,
    val lng: Double?,
    val authorId: String?,
    val authorName: String?,         // snapshot, only a fallback; live names come from members
)

data class CommentDoc(
    val id: String,                  // "" when creating: the repository generates it
    val reportId: String,
    val authorId: String?,
    val authorName: String?,
    val text: String,
    val createdAt: Long,
    val isEdited: Boolean,
    val byAdmin: Boolean,
)

data class MemberDoc(val uid: String, val displayName: String, val role: String)

data class LocationDoc(val id: String, val tag: String, val colorIndex: Int, val createdAt: Long)

object BoardRoles {
    const val ADMIN = "admin"
    const val REPORTER = "reporter"
    const val DEFAULT_WORKSPACE = "main"     // one shared board for now; ready for several workspaces later
}

interface BoardListener {
    fun onReports(list: List<ReportDoc>)
    fun onComments(list: List<CommentDoc>)
    fun onMembers(list: List<MemberDoc>)
    fun onLocations(list: List<LocationDoc>)
    fun onError(message: String)
}

/** Everything the board needs from a backend. Firestore implements it; a fake can stand in for tests. */
interface BoardRepository {
    fun start(workspaceId: String, listener: BoardListener)
    fun stop()

    fun upsertMember(uid: String, displayName: String)

    fun newReportId(): String
    fun addReport(doc: ReportDoc)
    fun toggleUpvote(reportId: String, uid: String, add: Boolean)
    fun moveReport(reportId: String, stage: String, resolvedAt: Long?, orderAt: Long)

    fun addComment(doc: CommentDoc)
    fun editComment(commentId: String, text: String)
    fun deleteComment(commentId: String)

    fun addLocation(doc: LocationDoc)
    fun deleteLocation(id: String)
}
