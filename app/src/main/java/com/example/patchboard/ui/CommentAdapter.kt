package com.example.patchboard.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.patchboard.databinding.ItemCommentBinding
import com.example.patchboard.model.Comment

class CommentAdapter(
    private val onSave: (commentId: String, newText: String) -> Unit,
    private val onDelete: (commentId: String) -> Unit,
) : ListAdapter<Comment, CommentAdapter.VH>(Diff) {

    private var currentUser = ""
    private var editingId: String? = null

    fun update(comments: List<Comment>, currentUser: String) {
        val userChanged = this.currentUser != currentUser
        this.currentUser = currentUser
        if (editingId != null && comments.none { it.id == editingId }) editingId = null
        submitList(comments)
        if (userChanged) notifyItemRangeChanged(0, itemCount) // owner actions depend on who's posting
    }

    inner class VH(val b: ItemCommentBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCommentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = getItem(position)
        val b = holder.b
        val isOwner = c.author.trim().equals(currentUser.trim(), ignoreCase = true)
        val editing = editingId == c.id

        b.tvAvatar.bindAvatar(c.author, 34)
        b.tvAuthor.text = c.author
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
