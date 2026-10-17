package com.example.patchboard.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.patchboard.R
import com.example.patchboard.databinding.ItemCardBinding
import com.example.patchboard.model.ReportCard
import com.example.patchboard.model.Stage

interface CardActions {
    fun onUpvote(cardId: Int)
    fun onOpenComments(cardId: Int)
    fun onMove(cardId: Int, forward: Boolean)
    fun onOpenImage(uri: String)
}

class CardAdapter(private val actions: CardActions) :
    ListAdapter<ReportCard, CardAdapter.VH>(Diff) {

    private var stage = Stage.REPORTED
    private var isAdmin = false

    /** Per-column state that isn't part of the card itself. */
    fun configure(stage: Stage, isAdmin: Boolean) {
        if (this.stage == stage && this.isAdmin == isAdmin) return
        this.stage = stage
        this.isAdmin = isAdmin
        notifyItemRangeChanged(0, itemCount)
    }

    inner class VH(val b: ItemCardBinding) : RecyclerView.ViewHolder(b.root) {
        val upvoteBg = b.btnUpvote.backgroundTintList
        val upvoteText = b.btnUpvote.textColors
        val upvoteIcon = b.btnUpvote.iconTint
        val upvoteStroke = b.btnUpvote.strokeColor
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val card = getItem(position)
        val b = holder.b
        val isFixed = stage == Stage.FIXED

        b.cardRoot.alpha = if (isFixed) 0.72f else 1f
        b.tvLocation.bindLocationTag(card.location)
        b.tvCreated.bindCreatedTime(card.createdAt)
        b.tvTitle.text = card.title
        b.tvAuthor.text = b.root.context.getString(R.string.card_by_author, card.authorName ?: b.root.context.getString(R.string.author_unknown))
        b.tvDescription.text = card.description
        b.tvPriority.bindPriority(card.priority)

        // Photo thumbnail → full-screen viewer
        val uri = card.imageUri
        if (uri != null) {
            b.imgThumb.visibility = android.view.View.VISIBLE
            Glide.with(b.imgThumb).load(Uri.parse(uri)).centerCrop().into(b.imgThumb)
            b.imgThumb.setOnClickListener { actions.onOpenImage(uri) }
        } else {
            Glide.with(b.imgThumb).clear(b.imgThumb)
            b.imgThumb.visibility = android.view.View.GONE
            b.imgThumb.setOnClickListener(null)
        }

        // Admin-only Back / Forward
        b.adminRow.visibility = if (isAdmin) android.view.View.VISIBLE else android.view.View.GONE
        val first = stage.ordinal == 0
        val last = stage.ordinal == Stage.values().lastIndex
        b.btnBack.isEnabled = !first
        b.btnBack.alpha = if (first) 0.45f else 1f
        b.btnForward.isEnabled = !last
        b.btnForward.alpha = if (last) 0.45f else 1f
        b.btnBack.setOnClickListener { actions.onMove(card.id, false) }
        b.btnForward.setOnClickListener { actions.onMove(card.id, true) }

        // Footer: comments
        b.btnComments.text = card.comments.size.toString()
        b.btnComments.contentDescription = b.root.context.getString(R.string.cd_view_comments, card.comments.size)
        b.btnComments.setOnClickListener { actions.onOpenComments(card.id) }

        // Footer: upvote (toggle)
        b.btnUpvote.text = card.upvotes.toString()
        b.btnUpvote.contentDescription = b.root.context.getString(R.string.cd_upvote_count, card.upvotes)
        b.btnUpvote.isSelected = card.upvotedByMe
        val ctx = b.root.context
        if (card.upvotedByMe) {
            val primary = android.content.res.ColorStateList.valueOf(ctx.color(R.color.pb_primary))
            val onPrimary = android.content.res.ColorStateList.valueOf(ctx.color(R.color.pb_on_primary))
            b.btnUpvote.backgroundTintList = primary
            b.btnUpvote.strokeColor = primary
            b.btnUpvote.setTextColor(onPrimary)
            b.btnUpvote.iconTint = onPrimary
        } else {
            b.btnUpvote.backgroundTintList = holder.upvoteBg
            b.btnUpvote.strokeColor = holder.upvoteStroke
            b.btnUpvote.setTextColor(holder.upvoteText)
            b.btnUpvote.iconTint = holder.upvoteIcon
        }
        b.btnUpvote.setOnClickListener { if (!isFixed) actions.onUpvote(card.id) }
    }

    private object Diff : DiffUtil.ItemCallback<ReportCard>() {
        override fun areItemsTheSame(a: ReportCard, b: ReportCard) = a.id == b.id
        override fun areContentsTheSame(a: ReportCard, b: ReportCard) = a == b
    }
}
