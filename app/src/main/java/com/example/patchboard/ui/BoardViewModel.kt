package com.example.patchboard.ui

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.patchboard.data.BoardListener
import com.example.patchboard.data.BoardRepository
import com.example.patchboard.data.BoardRoles
import com.example.patchboard.data.CommentDoc
import com.example.patchboard.data.FirestoreBoardRepository
import com.example.patchboard.data.LocationDoc
import com.example.patchboard.data.MemberDoc
import com.example.patchboard.data.ReportDoc
import com.example.patchboard.model.*

enum class AddTagResult { ADDED, INVALID, DUPLICATE }

/**
 * Single source of truth for the board. The data now lives in Firestore: this class listens to it,
 * rebuilds the columns on every change, and sends writes back through [BoardRepository].
 * The public API is the same as the old in-memory version, so the screens barely change.
 */
class BoardViewModel(private val repo: BoardRepository) : ViewModel() {

    constructor() : this(FirestoreBoardRepository())

    private val listeners = mutableListOf<() -> Unit>()

    // Raw data as last received from the server
    private var reportDocs: List<ReportDoc> = emptyList()
    private var commentDocs: List<CommentDoc> = emptyList()
    private var members: Map<String, MemberDoc> = emptyMap()
    private var locationDocs: List<LocationDoc> = emptyList()

    // What the UI reads
    private var columns: List<Column> = Stage.values().map { Column(it, emptyList()) }
    var locationTags: List<LocationTag> = LocationTag.DEFAULTS; private set
    var isLoaded: Boolean = false; private set              // true once the first reports snapshot arrived

    var currentUser: String = ""; private set                // display name of the signed-in account
    var currentUserId: String? = null; private set
    /** Admin rights come from the account's role on the server, not from a password. */
    val isAdmin: Boolean get() = currentUserId?.let { members[it]?.role == BoardRoles.ADMIN } == true

    var searchOpen: Boolean = false; private set
    var searchQuery: String = ""; private set
    var priorityFilter: Priority? = null; private set       // null = "All"
    var sortMode: SortMode = SortMode.OFF; private set
    var locationFilter: LocationTag? = null; private set    // null = all locations

    /** Changes whenever sort, filters or search change, so the lists know to jump back to the top. */
    var viewVersion: Int = 0; private set
    val viewKey: List<Any?> get() = listOf(sortMode, priorityFilter, locationFilter, searchQuery)

    /** Bumps when something should be brought into view: which column, and a change counter. */
    var focusToken: Int = 0; private set
    var focusColumn: Int = 0; private set
    private var pendingFocusColumn: Int? = null              // applied when our own change comes back from the server
    private fun focusOn(column: Int) { focusColumn = column; focusToken++ }

    /** One-shot error text (e.g. "no permission"); the activity shows it once and clears it. */
    var pendingError: String? = null; private set
    fun consumeError() { pendingError = null }

    // Report ids in the UI are Ints; Firestore ids are strings. This map is stable within a session.
    private val intIds = HashMap<String, Int>()
    private val docIds = HashMap<Int, String>()
    private var nextIntId = 1
    private fun intIdOf(docId: String): Int = intIds.getOrPut(docId) {
        val id = nextIntId++
        docIds[id] = docId
        id
    }

    private val localImages = HashMap<String, String>()     // photos are still device-local (see notes)
    private val lastUpvoteAt = HashMap<String, Long>()

    // ── Observation ──────────────────────────────────────────────────────────
    /** Calls [onChange] immediately and after every state change until [owner] is destroyed. */
    fun observe(owner: LifecycleOwner, onChange: () -> Unit) {
        listeners += onChange
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { listeners -= onChange }
        })
        onChange()
    }

    private fun notifyChanged() = listeners.toList().forEach { it() }

    override fun onCleared() { repo.stop() }

    // ── Server data in ───────────────────────────────────────────────────────
    private val boardListener = object : BoardListener {
        override fun onReports(list: List<ReportDoc>) { reportDocs = list; isLoaded = true; rebuild() }
        override fun onComments(list: List<CommentDoc>) { commentDocs = list; rebuild() }
        override fun onMembers(list: List<MemberDoc>) { members = list.associateBy { it.uid }; rebuild() }
        override fun onLocations(list: List<LocationDoc>) { locationDocs = list; rebuild() }
        override fun onError(message: String) { pendingError = message; notifyChanged() }
    }

    private fun parseStage(s: String): Stage = Stage.values().firstOrNull { it.name == s } ?: Stage.REPORTED
    private fun parsePriority(s: String): Priority = Priority.values().firstOrNull { it.name == s } ?: Priority.MEDIUM

    private fun nameOf(uid: String?, snapshot: String?): String? =
        uid?.let { members[it]?.displayName }?.takeIf { it.isNotBlank() } ?: snapshot?.takeIf { it.isNotBlank() }

    private fun locationFor(tag: String): LocationTag =
        locationTags.firstOrNull { it.tag == tag } ?: LocationTag.custom(tag, 0)

    private fun toCard(d: ReportDoc, comments: List<CommentDoc>): ReportCard {
        val me = currentUserId
        return ReportCard(
            id = intIdOf(d.id),
            location = locationFor(d.location),
            title = d.title,
            description = d.description,
            priority = parsePriority(d.priority),
            upvotes = d.upvotes,
            comments = comments.sortedBy { it.createdAt }.map { c ->
                Comment(
                    id = c.id,
                    author = nameOf(c.authorId, c.authorName) ?: UNKNOWN_AUTHOR,
                    text = c.text,
                    createdAt = c.createdAt,
                    isEdited = c.isEdited,
                    authorId = c.authorId,
                    byAdmin = c.byAdmin,
                )
            },
            createdAt = d.createdAt,
            imageUri = localImages[d.id],
            resolvedAt = d.resolvedAt,
            lat = d.lat,
            lng = d.lng,
            authorId = d.authorId,
            authorName = nameOf(d.authorId, d.authorName),
            upvotedBy = d.upvotedBy.toSet(),
            upvotedByMe = me != null && me in d.upvotedBy,
        )
    }

    private fun rebuild() {
        locationTags = LocationTag.DEFAULTS + locationDocs
            .filter { d -> LocationTag.DEFAULTS.none { it.tag.equals(d.tag, ignoreCase = true) } }
            .sortedBy { it.createdAt }
            .map { LocationTag.custom(it.tag, it.colorIndex) }
        val filter = locationFilter
        if (filter != null && filter !in locationTags) locationFilter = null

        val commentsByReport = commentDocs.groupBy { it.reportId }
        columns = Stage.values().map { stage ->
            Column(
                stage,
                reportDocs.filter { parseStage(it.stage) == stage }
                    .sortedByDescending { it.orderAt }
                    .map { toCard(it, commentsByReport[it.id].orEmpty()) },
            )
        }
        pendingFocusColumn?.let { focusOn(it); pendingFocusColumn = null }
        notifyChanged()
    }

    // ── Queries ──────────────────────────────────────────────────────────────
    private val normalizedQuery get() = searchQuery.trim().lowercase()

    private fun matchesSearch(card: ReportCard, q: String): Boolean {
        if (q.isEmpty()) return true
        val fields = buildList {
            add(card.title); add(card.description)
            add(card.location.tag); add(card.priority.label)
            card.authorName?.let { add(it) }
            card.comments.forEach { add(it.author); add(it.text) }
        }
        return fields.any { it.lowercase().contains(q) }
    }

    /** Columns after search, priority filter and sort. Never mutates stored data. */
    fun visibleColumns(): List<Column> = columns.map { col ->
        var cards = col.cards.filter {
            matchesSearch(it, normalizedQuery) &&
                    (priorityFilter == null || it.priority == priorityFilter) &&
                    (locationFilter == null || it.location == locationFilter)
        }
        cards = when (sortMode) {
            SortMode.OFF -> cards
            SortMode.DESC -> cards.sortedByDescending { it.upvotes }
            SortMode.ASC -> cards.sortedBy { it.upvotes }
        }
        Column(col.stage, cards)
    }

    fun searchResultCount(): Int = visibleColumns().sumOf { it.cards.size }

    fun findCard(cardId: Int): ReportCard? = columns.flatMap { it.cards }.firstOrNull { it.id == cardId }

    fun snapshot(): List<Column> = columns.map { it.copy(cards = it.cards.toList()) }

    // ── Reports ──────────────────────────────────────────────────────────────
    /** One vote per account, tap again to take it back. The server rules double-check this. */
    fun upvote(cardId: Int) {
        val uid = currentUserId ?: return
        val card = findCard(cardId) ?: return
        val docId = docIds[cardId] ?: return
        val inFixed = columns.firstOrNull { it.stage == Stage.FIXED }?.cards?.any { it.id == cardId } == true
        if (inFixed) return                                      // fixed reports are locked
        val now = System.currentTimeMillis()
        if (now - (lastUpvoteAt[docId] ?: 0L) < 400) return      // ignore double-taps while the write settles
        lastUpvoteAt[docId] = now
        repo.toggleUpvote(docId, uid, add = uid !in card.upvotedBy)
    }

    fun addReport(
        location: LocationTag, title: String, description: String,
        priority: Priority, imageUri: String?,
        lat: Double? = null, lng: Double? = null,
    ) {
        val uid = currentUserId ?: return
        val id = repo.newReportId()
        if (id.isEmpty()) return
        val now = System.currentTimeMillis()
        imageUri?.let { localImages[id] = it }
        pendingFocusColumn = 0
        repo.addReport(
            ReportDoc(
                id = id, location = location.tag, title = title.trim(), description = description.trim(),
                priority = priority.name, stage = Stage.REPORTED.name, upvotes = 0, upvotedBy = emptyList(),
                createdAt = now, orderAt = now, resolvedAt = null, lat = lat, lng = lng,
                authorId = uid, authorName = currentUser.ifBlank { null },
            )
        )
    }

    /** Admin only (also enforced by the server rules). The moved card goes to the top of its new column. */
    fun moveCard(cardId: Int, forward: Boolean) {
        if (!isAdmin) return
        val from = columns.indexOfFirst { col -> col.cards.any { it.id == cardId } }
        if (from == -1) return
        val to = if (forward) from + 1 else from - 1
        if (to !in columns.indices) return
        val docId = docIds[cardId] ?: return
        val now = System.currentTimeMillis()
        pendingFocusColumn = to
        repo.moveReport(
            docId, columns[to].stage.name,
            resolvedAt = if (columns[to].stage == Stage.FIXED) now else null,
            orderAt = now,
        )
    }

    // ── Comments ─────────────────────────────────────────────────────────────
    fun addComment(cardId: Int, text: String) {
        val uid = currentUserId ?: return
        val docId = docIds[cardId] ?: return
        repo.addComment(
            CommentDoc(
                id = "", reportId = docId, authorId = uid, authorName = currentUser.ifBlank { null },
                text = text, createdAt = System.currentTimeMillis(), isEdited = false, byAdmin = isAdmin,
            )
        )
    }

    fun deleteComment(cardId: Int, commentId: String) = repo.deleteComment(commentId)

    fun saveComment(cardId: Int, commentId: String, newText: String) = repo.editComment(commentId, newText)

    // ── Accounts ─────────────────────────────────────────────────────────────
    /** Called on sign-in and whenever the display name changes. Starts the live data for this account. */
    fun setAccount(id: String?, name: String) {
        if (id == null) { clearAccount(); return }
        currentUser = name.trim()
        if (currentUserId != id) {
            currentUserId = id
            resetData()
            repo.start(BoardRoles.DEFAULT_WORKSPACE, boardListener)
        }
        if (currentUser.isNotEmpty()) repo.upsertMember(id, currentUser)   // names are shown live from here
        notifyChanged()
    }

    fun clearAccount() {
        repo.stop()
        currentUserId = null
        currentUser = ""
        resetData()
        notifyChanged()
    }

    private fun resetData() {
        reportDocs = emptyList(); commentDocs = emptyList(); members = emptyMap(); locationDocs = emptyList()
        columns = Stage.values().map { Column(it, emptyList()) }
        locationTags = LocationTag.DEFAULTS
        pendingFocusColumn = null
        isLoaded = false
    }

    // The old password login is gone; these stay only so older code still compiles.
    @Deprecated("Admin rights now come from the account role") fun signIn(username: String, password: String) = false
    @Deprecated("Admin rights now come from the account role") fun signOut() {}

    // ── Location tags (admin) ────────────────────────────────────────────────
    fun addLocationTag(raw: String): AddTagResult {
        val tag = LocationTagNames.normalize(raw) ?: return AddTagResult.INVALID
        if (locationTags.any { it.tag.equals(tag, ignoreCase = true) }) return AddTagResult.DUPLICATE
        val doc = LocationDoc(LocationTagNames.idFor(tag), tag, locationDocs.size, System.currentTimeMillis())
        locationDocs = locationDocs + doc                        // show it right away; the server copy follows
        repo.addLocation(doc)
        rebuild()
        return AddTagResult.ADDED
    }

    /** How many reports (any stage) use this location. */
    fun locationUsage(tag: LocationTag): Int = columns.sumOf { col -> col.cards.count { it.location == tag } }

    /** Built-in tags stay forever; admin-added tags can go once no report uses them. */
    fun removeLocationTag(tag: LocationTag): Boolean {
        if (tag in LocationTag.DEFAULTS || locationUsage(tag) > 0) return false
        val id = LocationTagNames.idFor(tag.tag)
        locationDocs = locationDocs.filter { it.id != id }
        repo.deleteLocation(id)
        rebuild()
        return true
    }

    // ── Toolbar state ────────────────────────────────────────────────────────
    fun setPriorityFilter(p: Priority?) { priorityFilter = p; viewVersion++; notifyChanged() }

    fun setLocationFilter(l: LocationTag?) { locationFilter = l; viewVersion++; notifyChanged() }

    /** off → most upvotes first → fewest first → off */
    fun cycleSort() {
        sortMode = when (sortMode) {
            SortMode.OFF -> SortMode.DESC
            SortMode.DESC -> SortMode.ASC
            SortMode.ASC -> SortMode.OFF
        }
        viewVersion++
        notifyChanged()
    }

    fun setSearchOpen(open: Boolean) {
        searchOpen = open
        if (!open) searchQuery = ""
        viewVersion++
        notifyChanged()
    }

    fun setSearchQuery(q: String) { searchQuery = q; viewVersion++; notifyChanged() }

    private companion object {
        const val UNKNOWN_AUTHOR = "Unknown"
    }
}
