package com.example.patchboard.auth

import android.app.Activity
import android.content.Context
import androidx.lifecycle.LifecycleOwner

/** The signed-in person, as the rest of the app sees them (no Firebase types leak out of /auth). */
data class AccountUser(
    val id: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
)

enum class AuthErrorReason {
    INVALID_CREDENTIALS, INVALID_EMAIL, EMAIL_IN_USE, WEAK_PASSWORD,
    NETWORK, TOO_MANY_ATTEMPTS, CANCELLED, NO_GOOGLE_ACCOUNT, NOT_CONFIGURED, UNKNOWN,
}

data class AuthError(val reason: AuthErrorReason, val detail: String? = null)

/**
 * Everything the UI needs from an account system. Today it is backed by Firebase Authentication
 * ([FirebaseAuthRepository]); swap the implementation in [AuthProvider] to change backend.
 * All callbacks run on the main thread.
 */
interface AuthRepository {
    /** Synchronous: the persisted session, or null when signed out. */
    val currentUser: AccountUser?

    /** Calls [onChange] right away and again on every sign-in / sign-out / profile change, until [owner] is destroyed. */
    fun addListener(owner: LifecycleOwner, onChange: (AccountUser?) -> Unit)

    fun signInWithEmail(email: String, password: String, onSuccess: (AccountUser) -> Unit, onError: (AuthError) -> Unit)

    fun signUpWithEmail(
        name: String, email: String, password: String,
        onSuccess: (AccountUser) -> Unit, onError: (AuthError) -> Unit,
    )

    /** Shows the Google account chooser. [activity] is needed to host that system sheet. */
    fun signInWithGoogle(activity: Activity, onSuccess: (AccountUser) -> Unit, onError: (AuthError) -> Unit)

    /** Changes the name shown to others. [onDone] gets null on success; listeners are notified. */
    fun updateDisplayName(name: String, onDone: (AuthError?) -> Unit)

    /** [onDone] gets null on success. */
    fun sendPasswordReset(email: String, onDone: (AuthError?) -> Unit)

    fun signOut(context: Context)
}

/** Single place that decides which [AuthRepository] the app uses. */
object AuthProvider {
    private var override: AuthRepository? = null
    private val firebase: AuthRepository by lazy { FirebaseAuthRepository() }

    fun get(): AuthRepository = override ?: firebase

    /** For tests / previews. */
    fun set(repository: AuthRepository?) { override = repository }
}