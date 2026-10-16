package com.example.patchboard.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.patchboard.R
import com.example.patchboard.databinding.FragmentAuthBinding
import kotlin.math.max

/**
 * Full-screen sign in / create account screen. Shown by MainActivity above everything else while
 * nobody is signed in; MainActivity removes it as soon as the repository reports a user,
 * so this class never needs to navigate anywhere itself.
 */
class AuthFragment : Fragment() {

    private var _b: FragmentAuthBinding? = null
    private val b get() = _b!!

    private val auth get() = AuthProvider.get()
    private var signUp = false
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signUp = savedInstanceState?.getBoolean(STATE_SIGN_UP) ?: false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        _b = FragmentAuthBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Keep content clear of the status bar, navigation bar and keyboard.
        val base = b.scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(b.scroll) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(v.paddingLeft, bars.top, v.paddingRight, base + max(bars.bottom, ime))
            insets
        }
        ViewCompat.requestApplyInsets(b.scroll)

        b.btnSubmit.setOnClickListener { submit() }
        b.btnGoogle.setOnClickListener { google() }
        b.tvForgot.setOnClickListener { forgotPassword() }
        b.tvToggle.setOnClickListener {
            signUp = !signUp
            clearMessage()
            render()
        }
        b.etPassword.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SIGN_UP, signUp)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        b.tvTitle.setText(if (signUp) R.string.auth_title_sign_up else R.string.auth_title_sign_in)
        b.tvSubtitle.setText(if (signUp) R.string.auth_subtitle_sign_up else R.string.auth_subtitle_sign_in)
        b.groupName.isVisible = signUp
        b.tvForgot.isVisible = !signUp
        b.btnSubmit.setText(if (signUp) R.string.auth_action_sign_up else R.string.auth_action_sign_in)
        b.tvToggle.setText(if (signUp) R.string.auth_toggle_to_sign_in else R.string.auth_toggle_to_sign_up)
        setBusy(busy)
    }

    private fun setBusy(value: Boolean) {
        busy = value
        b.progress.isVisible = value
        listOf(b.btnSubmit, b.btnGoogle, b.tvToggle, b.tvForgot).forEach { it.isEnabled = !value }
    }

    private fun showMessage(text: String, isError: Boolean) {
        b.tvMessage.text = text
        b.tvMessage.setTextColor(
            ContextCompat.getColor(requireContext(), if (isError) R.color.prio_high_text else R.color.pb_on_surface_variant)
        )
        b.tvMessage.isVisible = true
    }

    private fun clearMessage() { b.tvMessage.isVisible = false }

    // ---------------------------------------------------------------- actions

    private fun submit() {
        if (busy) return
        val name = b.etName.text.toString()
        val email = b.etEmail.text.toString().trim()
        val password = b.etPassword.text.toString()

        val problem = when {
            signUp && !AuthValidation.isValidName(name) -> R.string.auth_err_name
            !AuthValidation.isValidEmail(email) -> R.string.auth_err_email
            password.isEmpty() -> R.string.auth_err_password_empty
            signUp && !AuthValidation.isValidPassword(password) -> R.string.auth_err_password_short
            else -> null
        }
        if (problem != null) { showMessage(getString(problem), isError = true); return }

        clearMessage()
        hideKeyboard()
        setBusy(true)
        if (signUp) auth.signUpWithEmail(name, email, password, { done() }, ::fail)
        else auth.signInWithEmail(email, password, { done() }, ::fail)
    }

    private fun google() {
        if (busy) return
        clearMessage()
        hideKeyboard()
        setBusy(true)
        auth.signInWithGoogle(requireActivity(), { done() }, ::fail)
    }

    private fun forgotPassword() {
        if (busy) return
        val email = b.etEmail.text.toString().trim()
        if (!AuthValidation.isValidEmail(email)) {
            showMessage(getString(R.string.auth_reset_enter_email), isError = true)
            return
        }
        setBusy(true)
        auth.sendPasswordReset(email) { error ->
            if (_b == null) return@sendPasswordReset
            setBusy(false)
            if (error == null) showMessage(getString(R.string.auth_reset_sent), isError = false)
            else showMessage(getString(messageFor(error)), isError = true)
        }
    }

    /** On success MainActivity removes this screen, so only the busy flag needs resetting. */
    private fun done() {
        if (_b != null) setBusy(false)
    }

    private fun fail(error: AuthError) {
        if (_b == null) return
        setBusy(false)
        if (error.reason == AuthErrorReason.CANCELLED) return      // user closed the Google sheet
        showMessage(getString(messageFor(error)), isError = true)
    }

    private fun messageFor(e: AuthError): Int = when (e.reason) {
        AuthErrorReason.INVALID_CREDENTIALS -> R.string.auth_err_credentials
        AuthErrorReason.INVALID_EMAIL -> R.string.auth_err_email
        AuthErrorReason.EMAIL_IN_USE -> R.string.auth_err_email_in_use
        AuthErrorReason.WEAK_PASSWORD -> R.string.auth_err_weak_password
        AuthErrorReason.NETWORK -> R.string.auth_err_network
        AuthErrorReason.TOO_MANY_ATTEMPTS -> R.string.auth_err_too_many
        AuthErrorReason.NO_GOOGLE_ACCOUNT -> R.string.auth_err_no_google
        AuthErrorReason.NOT_CONFIGURED -> R.string.auth_err_not_configured
        AuthErrorReason.CANCELLED, AuthErrorReason.UNKNOWN -> R.string.auth_err_unknown
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(b.root.windowToken, 0)
    }

    private companion object {
        const val STATE_SIGN_UP = "sign_up"
    }
}
