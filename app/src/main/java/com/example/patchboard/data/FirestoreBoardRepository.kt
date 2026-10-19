package com.example.patchboard.data

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration

/**
 * Firestore layout (one document tree per workspace):
 *   workspaces/{ws}/members/{uid}     { displayName, role }
 *   workspaces/{ws}/reports/{id}      { location, title, ..., stage, upvotes, upvotedBy[], orderAt, authorId }
 *   workspaces/{ws}/comments/{id}     { reportId, authorId, text, createdAt, isEdited, byAdmin }
 *   workspaces/{ws}/locations/{id}    { tag, colorIndex, createdAt }     (admin-added tags; id = lower-case name)
 * Who may do what is enforced by firestore.rules, not by this app.
 */
class FirestoreBoardRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : BoardRepository {

    private var ws: DocumentReference? = null
    private var listener: BoardListener? = null
    private val registrations = mutableListOf<ListenerRegistration>()

    // ── Listening ────────────────────────────────────────────────────────────
    override fun start(workspaceId: String, listener: BoardListener) {
        stop()
        val root = db.collection("workspaces").document(workspaceId)
        ws = root
        this.listener = listener

        registrations += root.collection("reports").addSnapshotListener { snap, e ->
            if (e != null) { listener.onError(e.userMessage()); return@addSnapshotListener }
            listener.onReports(snap?.documents.orEmpty().mapNotNull { it.toReportDoc() })
        }
        registrations += root.collection("comments").addSnapshotListener { snap, e ->
            if (e != null) { listener.onError(e.userMessage()); return@addSnapshotListener }
            listener.onComments(snap?.documents.orEmpty().mapNotNull { it.toCommentDoc() })
        }
        registrations += root.collection("members").addSnapshotListener { snap, e ->
            if (e != null) { listener.onError(e.userMessage()); return@addSnapshotListener }
            listener.onMembers(snap?.documents.orEmpty().map {
                MemberDoc(it.id, it.getString("displayName").orEmpty(), it.getString("role") ?: BoardRoles.REPORTER)
            })
        }
        registrations += root.collection("locations").addSnapshotListener { snap, e ->
            if (e != null) { listener.onError(e.userMessage()); return@addSnapshotListener }
            listener.onLocations(snap?.documents.orEmpty().mapNotNull { d ->
                val tag = d.getString("tag") ?: return@mapNotNull null
                LocationDoc(d.id, tag, (d.getLong("colorIndex") ?: 0L).toInt(), d.getLong("createdAt") ?: 0L)
            })
        }
    }

    override fun stop() {
        registrations.forEach { it.remove() }
        registrations.clear()
        ws = null
        listener = null
    }

    // ── Members ──────────────────────────────────────────────────────────────
    /** First sign-in creates the member as a plain reporter; later calls only refresh the display name. */
    override fun upsertMember(uid: String, displayName: String) {
        val ref = ws?.collection("members")?.document(uid) ?: return
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) {
                tx.set(ref, mapOf("role" to BoardRoles.REPORTER, "displayName" to displayName))
            } else if (snap.getString("displayName") != displayName) {
                tx.update(ref, "displayName", displayName)
            }
            null
        }.addOnFailureListener { fail(it) }
    }

    // ── Reports ──────────────────────────────────────────────────────────────
    override fun newReportId(): String = ws?.collection("reports")?.document()?.id ?: ""

    override fun addReport(doc: ReportDoc) {
        val ref = ws?.collection("reports")?.document(doc.id) ?: return
        val data = buildMap<String, Any> {
            put("location", doc.location)
            put("title", doc.title)
            put("description", doc.description)
            put("priority", doc.priority)
            put("stage", doc.stage)
            put("upvotes", doc.upvotes)
            put("upvotedBy", doc.upvotedBy)
            put("createdAt", doc.createdAt)
            put("orderAt", doc.orderAt)
            doc.authorId?.let { put("authorId", it) }
            doc.authorName?.let { put("authorName", it) }
            doc.lat?.let { put("lat", it) }
            doc.lng?.let { put("lng", it) }
        }
        ref.set(data).addOnFailureListener { fail(it) }
    }

    override fun toggleUpvote(reportId: String, uid: String, add: Boolean) {
        val ref = ws?.collection("reports")?.document(reportId) ?: return
        ref.update(
            mapOf(
                "upvotedBy" to if (add) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid),
                "upvotes" to FieldValue.increment(if (add) 1L else -1L),
            )
        ).addOnFailureListener { fail(it) }
    }

    override fun moveReport(reportId: String, stage: String, resolvedAt: Long?, orderAt: Long) {
        val ref = ws?.collection("reports")?.document(reportId) ?: return
        ref.update(
            mapOf(
                "stage" to stage,
                "resolvedAt" to (resolvedAt ?: FieldValue.delete()),
                "orderAt" to orderAt,
            )
        ).addOnFailureListener { fail(it) }
    }

    // ── Comments ─────────────────────────────────────────────────────────────
    override fun addComment(doc: CommentDoc) {
        val col = ws?.collection("comments") ?: return
        val data = buildMap<String, Any> {
            put("reportId", doc.reportId)
            doc.authorId?.let { put("authorId", it) }
            doc.authorName?.let { put("authorName", it) }
            put("text", doc.text)
            put("createdAt", doc.createdAt)
            put("isEdited", false)
            put("byAdmin", doc.byAdmin)
        }
        col.document().set(data).addOnFailureListener { fail(it) }
    }

    override fun editComment(commentId: String, text: String) {
        ws?.collection("comments")?.document(commentId)
            ?.update(mapOf("text" to text, "isEdited" to true))
            ?.addOnFailureListener { fail(it) }
    }

    override fun deleteComment(commentId: String) {
        ws?.collection("comments")?.document(commentId)?.delete()?.addOnFailureListener { fail(it) }
    }

    // ── Locations ────────────────────────────────────────────────────────────
    override fun addLocation(doc: LocationDoc) {
        ws?.collection("locations")?.document(doc.id)
            ?.set(mapOf("tag" to doc.tag, "colorIndex" to doc.colorIndex, "createdAt" to doc.createdAt))
            ?.addOnFailureListener { fail(it) }
    }

    override fun deleteLocation(id: String) {
        ws?.collection("locations")?.document(id)?.delete()?.addOnFailureListener { fail(it) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun fail(e: Exception) { listener?.onError(e.userMessage()) }

    private fun Exception.userMessage(): String =
        if ((this as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
            "You don't have permission to do that."
        else
            "Something went wrong talking to the server. Check your connection."

    private fun DocumentSnapshot.toReportDoc(): ReportDoc? {
        val title = getString("title") ?: return null
        val created = getLong("createdAt") ?: 0L
        return ReportDoc(
            id = id,
            location = getString("location").orEmpty(),
            title = title,
            description = getString("description").orEmpty(),
            priority = getString("priority") ?: "MEDIUM",
            stage = getString("stage") ?: "REPORTED",
            upvotes = (getLong("upvotes") ?: 0L).toInt(),
            upvotedBy = (get("upvotedBy") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            createdAt = created,
            orderAt = getLong("orderAt") ?: created,
            resolvedAt = getLong("resolvedAt"),
            lat = getDouble("lat"),
            lng = getDouble("lng"),
            authorId = getString("authorId"),
            authorName = getString("authorName"),
        )
    }

    private fun DocumentSnapshot.toCommentDoc(): CommentDoc? {
        val reportId = getString("reportId") ?: return null
        return CommentDoc(
            id = id,
            reportId = reportId,
            authorId = getString("authorId"),
            authorName = getString("authorName"),
            text = getString("text").orEmpty(),
            createdAt = getLong("createdAt") ?: 0L,
            isEdited = getBoolean("isEdited") ?: false,
            byAdmin = getBoolean("byAdmin") ?: false,
        )
    }
}
