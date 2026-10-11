package com.example.patchboard.ui

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.patchboard.data.SeedData
import com.example.patchboard.model.*

/**
 * Single source of truth for the board (the equivalent of the useState hooks in App.tsx).
 * Survives rotation / dark-mode recreation. In-memory only, like the prototype.
 */
class BoardViewModel : ViewModel() {

    private companion object {
        // PROTOTYPE ONLY: hard-coded like the React demo. Replace with real auth before shipping.
        const val ADMIN_USERNAME = "admin"
        const val ADMIN_PASSWORD = "admin123"
    }

    private var columns: List<Column> = SeedData.columns()
    private var nextId = 100
    private val listeners = mutableListOf<() -> Unit>()

    var currentUser: String = "Rere"; private set
    var isAdmin: Boolean = false; private set
    var searchOpen: Boolean = false; private set
    var searchQuery: String = ""; private set
    var priorityFilter: Priority? = null; private set   // null = "All"
    var sortMode: SortMode = SortMode.OFF; private set

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

    // ── Queries ──────────────────────────────────────────────────────────────
    private val normalizedQuery get() = searchQuery.trim().lowercase()

    private fun matchesSearch(card: ReportCard, q: String): Boolean {
        if (q.isEmpty()) return true
        val fields = buildList {
            add(card.title); add(card.description)
            add(card.location.tag); add(card.priority.label)
            card.comments.forEach { add(it.author); add(it.text) }
        }
        return fields.any { it.lowercase().contains(q) }
    }

    /** Columns after search, priority filter and sort. Never mutates stored data. */
    fun visibleColumns(): List<Column> = columns.map { col ->
        var cards = col.cards.filter {
            matchesSearch(it, normalizedQuery) &&
                    (priorityFilter == null || it.priority == priorityFilter)
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

    private fun updateCard(cardId: Int, transform: (ReportCard) -> ReportCard) {
        columns = columns.map { col ->
            col.copy(cards = col.cards.map { if (it.id == cardId) transform(it) else it })
        }
        notifyChanged()
    }

    // ── Reports ──────────────────────────────────────────────────────────────
    fun upvote(cardId: Int) {
        val inFixed = columns.firstOrNull { it.stage == Stage.FIXED }?.cards?.any { it.id == cardId } == true
        if (inFixed) return // fixed reports can't be upvoted
        updateCard(cardId) { it.copy(upvotes = it.upvotes + 1) }
    }

    fun addReport(
        location: LocationTag, title: String, description: String,
        priority: Priority, imageUri: String?,
    ) {
        val card = ReportCard(
            id = nextId++, location = location, title = title.trim(),
            description = description.trim(), priority = priority, upvotes = 0,
            comments = emptyList(), createdAt = System.currentTimeMillis(), imageUri = imageUri,
        )
        columns = columns.mapIndexed { i, col -> if (i == 0) col.copy(cards = listOf(card) + col.cards) else col }
        notifyChanged()
    }

    /** Admin only (enforced in UI). Moved card goes to the top of the destination column. */
    fun moveCard(cardId: Int, forward: Boolean) {
        val from = columns.indexOfFirst { col -> col.cards.any { it.id == cardId } }
        if (from == -1) return
        val to = if (forward) from + 1 else from - 1
        if (to !in columns.indices) return
        val moving = columns[from].cards.first { it.id == cardId }
        // Analytics: remember when a report was completed (cleared if it is moved back out of Fixed).
        val card = when (columns[to].stage) {
            Stage.FIXED -> moving.copy(resolvedAt = System.currentTimeMillis())
            else -> moving.copy(resolvedAt = null)
        }
        columns = columns.mapIndexed { i, col ->
            when (i) {
                from -> col.copy(cards = col.cards.filter { it.id != cardId })
                to -> col.copy(cards = listOf(card) + col.cards)
                else -> col
            }
        }
        notifyChanged()
    }

    // ── Comments ─────────────────────────────────────────────────────────────
    fun addComment(cardId: Int, text: String) {
        val comment = Comment("$cardId-${System.currentTimeMillis()}", currentUser, text, System.currentTimeMillis())
        updateCard(cardId) { it.copy(comments = it.comments + comment) }
    }

    fun deleteComment(cardId: Int, commentId: String) =
        updateCard(cardId) { c -> c.copy(comments = c.comments.filter { it.id != commentId }) }

    fun saveComment(cardId: Int, commentId: String, newText: String) =
        updateCard(cardId) { c ->
            c.copy(comments = c.comments.map {
                if (it.id == commentId) it.copy(text = newText, isEdited = true) else it
            })
        }

    fun changeUser(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) { currentUser = trimmed; notifyChanged() }
    }

    // ── Toolbar state ────────────────────────────────────────────────────────
    fun setPriorityFilter(p: Priority?) { priorityFilter = p; notifyChanged() }

    /** off → most upvotes first → fewest first → off */
    fun cycleSort() {
        sortMode = when (sortMode) {
            SortMode.OFF -> SortMode.DESC
            SortMode.DESC -> SortMode.ASC
            SortMode.ASC -> SortMode.OFF
        }
        notifyChanged()
    }

    fun setSearchOpen(open: Boolean) {
        searchOpen = open
        if (!open) searchQuery = ""
        notifyChanged()
    }

    fun setSearchQuery(q: String) { searchQuery = q; notifyChanged() }

    // ── Admin ────────────────────────────────────────────────────────────────
    fun signIn(username: String, password: String): Boolean {
        val ok = username == ADMIN_USERNAME && password == ADMIN_PASSWORD
        if (ok) { isAdmin = true; notifyChanged() }
        return ok
    }

    fun signOut() { isAdmin = false; notifyChanged() }

    fun snapshot(): List<Column> = columns.map { it.copy(cards = it.cards.toList()) }
}