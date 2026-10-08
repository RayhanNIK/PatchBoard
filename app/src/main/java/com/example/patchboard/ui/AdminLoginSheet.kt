package com.example.patchboard.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.example.patchboard.R
import com.example.patchboard.databinding.SheetAdminLoginBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.max

class AdminLoginSheet : BottomSheetDialogFragment() {

    private var _b: SheetAdminLoginBinding? = null
    private val b get() = _b!!
    private lateinit var vm: BoardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(requireActivity())[BoardViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = SheetAdminLoginBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return
        val sheet = dialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        BottomSheetBehavior.from(sheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val basePad = requireContext().dp(16)
        ViewCompat.setOnApplyWindowInsetsListener(b.sheetRoot) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            b.formBody.updatePadding(bottom = basePad + max(ime, nav))
            insets
        }

        b.etUsername.doAfterTextChanged { clearError() }
        b.etPassword.doAfterTextChanged { clearError() }
        b.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        b.btnSignIn.setOnClickListener { submit() }
        b.etUsername.requestFocus()
    }

    private fun submit() {
        if (vm.signIn(b.etUsername.text.toString(), b.etPassword.text.toString())) {
            dismiss()
        } else {
            b.tvError.visibility = View.VISIBLE
            b.etUsername.setBackgroundResource(R.drawable.bg_input_error)
            b.etPassword.setBackgroundResource(R.drawable.bg_input_error)
        }
    }

    private fun clearError() {
        if (b.tvError.visibility == View.VISIBLE) {
            b.tvError.visibility = View.GONE
            b.etUsername.setBackgroundResource(R.drawable.bg_input)
            b.etPassword.setBackgroundResource(R.drawable.bg_input)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
