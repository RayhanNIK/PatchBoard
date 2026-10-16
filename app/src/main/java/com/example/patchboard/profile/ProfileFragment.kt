package com.example.patchboard.profile

import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.patchboard.R
import com.example.patchboard.auth.AuthProvider
import com.example.patchboard.auth.AuthValidation
import com.example.patchboard.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The Profile tab: who is signed in, and a Sign out button. */
class ProfileFragment : Fragment() {

    private var _b: FragmentProfileBinding? = null
    private val b get() = _b!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentProfileBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val headerTop = b.header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(b.header) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, headerTop + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(b.header)

        b.tvAvatar.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(requireContext(), R.color.pb_primary))
        }

        b.btnEditName.setOnClickListener { showEditNameDialog() }
        b.btnSignOut.setOnClickListener { AuthProvider.get().signOut(requireContext()) }

        AuthProvider.get().addListener(viewLifecycleOwner) { user ->
            if (_b == null) return@addListener
            b.tvName.text = user?.displayName ?: getString(R.string.profile_signed_out_hint)
            b.tvEmail.text = user?.email.orEmpty()
            b.tvEmail.isVisible = !user?.email.isNullOrBlank()
            b.tvAvatar.text = user?.displayName?.trim()?.firstOrNull()?.uppercase() ?: "?"
        }
    }

    /** Rename dialog built in code (no extra layout). Stays open until the name is valid and saved. */
    private fun showEditNameDialog() {
        val ctx = requireContext()
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(ctx).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            setSingleLine()
            setText(AuthProvider.get().currentUser?.displayName.orEmpty())
            setSelection(text.length)
        }
        val holder = FrameLayout(ctx).apply { setPadding(pad, pad / 2, pad, 0); addView(input) }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.profile_edit_title)
            .setView(holder)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.profile_save, null)      // real handler below, so errors keep the dialog open
            .create()
        dialog.show()

        val save = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        save.setOnClickListener {
            val name = input.text.toString()
            if (!AuthValidation.isValidName(name)) {
                input.error = getString(R.string.auth_err_name)
                return@setOnClickListener
            }
            save.isEnabled = false
            AuthProvider.get().updateDisplayName(name) { error ->
                if (error == null) {
                    dialog.dismiss()
                    if (_b != null) Toast.makeText(ctx, R.string.profile_name_saved, Toast.LENGTH_SHORT).show()
                } else {
                    save.isEnabled = true
                    input.error = getString(R.string.auth_err_unknown)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}