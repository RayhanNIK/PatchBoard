package com.example.patchboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.patchboard.R
import com.example.patchboard.databinding.ItemCommentBinding
import com.example.patchboard.model.Comment

class CommentAdapter(
    private val onSave: (commentId: String, newText: String) -> Unit,
    private val onDelete: (commentId: String) -> Unit,
) : ListAdapter<Comment, CommentAdapter.VH>(Diff) {

    private var currentUserId: String? = null
    private var reportAuthorId: String? = null
    private var reportAuthorName: String? = null
    private var editingId: String? = null

    fun update(
        comments: List<Comment>,
        currentUserId: String?,
        reportAuthorId: String?,
        reportAuthorName: String?,
    ) {
        val changed = this.currentUserId != currentUserId ||
                this.reportAuthorId != reportAuthorId || this.reportAuthorName != reportAuthorName
        this.currentUserId = currentUserId
        this.reportAuthorId = reportAuthorId
        this.reportAuthorName = reportAuthorName
        if (editingId != null && comments.none { it.id == editingId }) editingId = null
        submitList(comments)
        if (changed) notifyItemRangeChanged(0, itemCount) // owner actions and badges depend on these
    }

    inner class VH(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Is this comment from the person who filed the report? Seed data has no ids, so it matches by name. */
    private fun isReportAuthor(c: Comment): Boolean = when {
        c.authorId != null && reportAuthorId != null -> c.authorId == reportAuthorId
        c.authorId == null && reportAuthorId == null && reportAuthorName != null ->
            c.author.trim().equals(reportAuthorName!!.trim(), ignoreCase = true)
        else -> false
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        val b = holder.b
        val isOwner = c.authorId != null && c.authorId == currentUserId
        val editing = editingId == c.id

        b.tvAvatar.bindAvatar(c.author, 34)
        b.tvAuthor.text = c.author

        val author = isReportAuthor(c)
        val badge = when {
            c.byAdmin && author -> R.string.badge_admin_author
            c.byAdmin -> R.string.badge_admin
            author -> R.string.badge_author
            else -> 0
        }
        if (badge != 0) { b.tvBadge.setText(badge); b.tvBadge.visibility = View.VISIBLE }
        else b.tvBadge.visibility = View.GONE

        b.tvTime.bindCreatedTime(c.createdAt)
        b.tvEdited.visibility = if (c.isEdited) View.VISIBLE else View.GONE
        b.tvText.text = c.text
        b.divider.visibility = if (position < itemCount - 1) View.VISIBLE else View.INVISIBLE

        b.tvText.visibility = if (editing) View.GONE else View.VISIBLE
        b.editArea.visibility = if (editing) View.VISIBLE else View.GONE
        b.ownerRow.visibility = if (isOwner && !editing) View.VISIBLE else View.GONE

        if (editing) {
            b.etEdit.setText(c.text)
            b.etEdit.setSelection(b.etEdit.text.length)
            b.etEdit.requestFocus()
        }

        b.btnEdit.setOnClickListener {
            editingId = c.id
            notifyItemChanged(holder.bindingAdapterPosition)
        }
        b.btnCancel.setOnClickListener {
            editingId = null
            notifyItemChanged(holder.bindingAdapterPosition)
        }
        b.btnSave.setOnClickListener {
            val text = b.etEdit.text.toString().trim()
            if (text.isNotEmpty()) {
                editingId = null
                onSave(c.id, text)
                notifyItemChanged(holder.bindingAdapterPosition)
            }
        }
        b.btnDelete.setOnClickListener { onDelete(c.id) }
    }

    private object Diff : DiffUtil.ItemCallback<Comment>() {
        override fun areItemsTheSame(a: Comment, b: Comment) = a.id == b.id
        override fun areContentsTheSame(a: Comment, b: Comment) = a == b
    }
}
