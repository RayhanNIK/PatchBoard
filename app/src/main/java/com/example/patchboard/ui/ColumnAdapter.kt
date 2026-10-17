package com.example.patchboard.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.patchboard.R
import com.example.patchboard.databinding.ItemColumnBinding
import com.example.patchboard.model.Column

class ColumnAdapter(private val actions: CardActions) : RecyclerView.Adapter<ColumnAdapter.VH>() {

    private var columns: List<Column> = emptyList()
    private var isAdmin = false

    private var viewKey: Any? = null
    private var focusToken = 0
    private var focusColumn = 0
    private var addEpoch = 0

    private var scrollEpoch = 0

    fun submit(columns: List<Column>, isAdmin: Boolean, viewKey: Any?, focusToken: Int, focusColumn: Int) {
        val firstLoad = this.columns.isEmpty()
        if (viewKey != this.viewKey) { this.viewKey = viewKey; scrollEpoch++ }
        if (focusToken != this.focusToken) { this.focusToken = focusToken; this.focusColumn = focusColumn; addEpoch++ }
        this.columns = columns
        this.isAdmin = isAdmin
        if (firstLoad) notifyDataSetChanged() else notifyItemRangeChanged(0, columns.size)
    }

    inner class VH(val b: ItemColumnBinding) : RecyclerView.ViewHolder(b.root) {
        val cardAdapter = CardAdapter(actions)

        var seenEpoch = -1
        var seenAddEpoch = -1


        init {
            b.cardsRecycler.layoutManager = LinearLayoutManager(b.root.context)
            b.cardsRecycler.adapter = cardAdapter
        }
    }

    override fun getItemCount() = columns.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemColumnBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val col = columns[position]
        val ctx = holder.b.root.context
        holder.b.root.tintBackground(
            ctx.color(if (position == 0) R.color.pb_column_bg_first else R.color.pb_column_bg)
        )
        holder.b.dotAccent.tintBackground(ctx.color(col.stage.accentRes))
        holder.b.tvColumnTitle.text = col.stage.title
        holder.b.tvCount.text = col.cards.size.toString()
        holder.cardAdapter.configure(col.stage, isAdmin)

        val viewJump = holder.seenEpoch != scrollEpoch
        val addJump = holder.seenAddEpoch != addEpoch
        holder.seenEpoch = scrollEpoch
        holder.seenAddEpoch = addEpoch
        val jump = viewJump || (position == focusColumn && addJump)    // a new report only moves the first column
        holder.cardAdapter.submitList(col.cards) {
            if (jump) holder.b.cardsRecycler.scrollToPosition(0)
        }
    }
}