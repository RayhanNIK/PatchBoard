package com.example.patchboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.patchboard.R
import com.example.patchboard.databinding.SheetCommentsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Bottom sheet showing a report's comment thread (the web version's CommentModal). */
class CommentsSheet : BottomSheetDialogFragment() {

    private var _b: SheetCommentsBinding? = null
    private val b get() = _b!!
    private lateinit var vm: BoardViewModel
    private lateinit var adapter: CommentAdapter
    private var cardId = -1
    private var lastCommentCount = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cardId = requireArguments().getInt(ARG_CARD_ID)
        vm = ViewModelProvider(requireActivity())[BoardViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = SheetCommentsBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onStart() {
        super.onStart()
        // ~86% of screen height, fully expanded (matches the prototype's 86vh sheet).
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        BottomSheetBehavior.from(sheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isFitToContents = false // Allows sheet to adjust properly with insets
        }

        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Keep the input bar above the keyboard / gesture bar.
        ViewCompat.setOnApplyWindowInsetsListener(b.sheetRoot) { _, insets ->
            b.inputBar.updatePadding(bottom = requireContext().dp(12))

            insets
        }

        adapter = CommentAdapter(
            onSave = { id, text -> vm.saveComment(cardId, id, text) },
            onDelete = { id -> vm.deleteComment(cardId, id) },
        )
        b.commentsRecycler.layoutManager = LinearLayoutManager(requireContext())
        b.commentsRecycler.adapter = adapter

        b.btnClose.setOnClickListener { dismiss() }

        // Compose box
        b.etComment.doAfterTextChanged { updateSendButton() }
        b.etComment.setOnTouchListener { v, event ->
            if (v.canScrollVertically(-1) || v.canScrollVertically(1)) {
                v.parent.requestDisallowInterceptTouchEvent(true)
            }
            if (event.actionMasked == MotionEvent.ACTION_UP ||
                event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }

        b.btnSend.setOnClickListener { send() }

        vm.observe(viewLifecycleOwner) { render() }
    }

    private fun send() {
        val text = b.etComment.text.toString().trim()
        if (text.isEmpty()) return
        vm.addComment(cardId, text)
        b.etComment.setText("")
    }

    private fun render() {
        val card = vm.findCard(cardId)
        if (card == null) { dismiss(); return }

        b.tvLocation.bindLocationTag(card.location)
        b.tvTitle.text = card.title
        val n = card.comments.size
        b.tvCount.text = resources.getQuantityString(R.plurals.comment_count, n, n)

        adapter.update(card.comments, vm.currentUserId, card.authorId, card.authorName)
        b.emptyState.visibility = if (n == 0) View.VISIBLE else View.GONE
        if (n > lastCommentCount && n > 0 && lastCommentCount != -1) {
            b.commentsRecycler.scrollToPosition(n - 1) // newest comment into view
        } else if (lastCommentCount == -1 && n > 0) {
            b.commentsRecycler.scrollToPosition(n - 1) // open at the bottom like the web version
        }
        lastCommentCount = n

        b.tvInputAvatar.bindAvatar(vm.currentUser, 30)
        updateSendButton()
    }

    private fun updateSendButton() {
        val enabled = b.etComment.text.toString().isNotBlank()
        val ctx = requireContext()
        b.btnSend.isEnabled = enabled
        b.btnSend.tintBackground(ctx.color(if (enabled) R.color.pb_primary else R.color.pb_outline))
        b.btnSend.setColorFilter(
            ctx.color(if (enabled) R.color.pb_on_primary else R.color.pb_btn_disabled_text)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    companion object {
        private const val ARG_CARD_ID = "card_id"
        fun newInstance(cardId: Int) = CommentsSheet().apply {
            arguments = Bundle().apply { putInt(ARG_CARD_ID, cardId) }
        }
    }
}
