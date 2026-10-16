package com.example.patchboard.auth

import android.app.Activity
import android.content.Context
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest

/** Firebase Authentication: email + password, and Google through Android's Credential Manager. */
class FirebaseAuthRepository : AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val listeners = LinkedHashSet<(AccountUser?) -> Unit>()

    init {
        auth.addAuthStateListener { notifyChanged() }
    }

    override val currentUser: AccountUser? get() = auth.currentUser?.toAccount()

    private fun notifyChanged() {
        val user = currentUser
        listeners.toList().forEach { it(user) }
    }

    override fun addListener(owner: LifecycleOwner, onChange: (AccountUser?) -> Unit) {
        listeners += onChange
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { listeners -= onChange }
        })
        onChange(currentUser)
    }

    // ── Email + password ─────────────────────────────────────────────────────
    override fun signInWithEmail(
        email: String, password: String,
        onSuccess: (AccountUser) -> Unit, onError: (AuthError) -> Unit,
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { r ->
                val user = r.user
                if (user != null) onSuccess(user.toAccount()) else onError(AuthError(AuthErrorReason.UNKNOWN))
            }
            .addOnFailureListener { onError(it.toAuthError()) }
    }

    override fun signUpWithEmail(
        name: String, email: String, password: String,
        onSuccess: (AccountUser) -> Unit, onError: (AuthError) -> Unit,
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { r ->
                val user = r.user
                if (user == null) { onError(AuthError(AuthErrorReason.UNKNOWN)); return@addOnSuccessListener }
                // The account exists now; store the display name, then tell listeners again so the
                // app shows the real name instead of the e-mail prefix.
                val profile = UserProfileChangeRequest.Builder().setDisplayName(AuthValidation.cleanName(name)).build()
                user.updateProfile(profile).addOnCompleteListener {
                    notifyChanged()
                    onSuccess(user.toAccount())
                }
            }
            .addOnFailureListener { onError(it.toAuthError()) }
    }

    override fun updateDisplayName(name: String, onDone: (AuthError?) -> Unit) {
        val user = auth.currentUser
        if (user == null) { onDone(AuthError(AuthErrorReason.UNKNOWN)); return }
        val profile = UserProfileChangeRequest.Builder().setDisplayName(AuthValidation.cleanName(name)).build()
        user.updateProfile(profile).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                notifyChanged()
                onDone(null)
            } else {
                onDone((task.exception ?: IllegalStateException()).toAuthError())
            }
        }
    }

    override fun sendPasswordReset(email: String, onDone: (AuthError?) -> Unit) {
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { onDone(it.toAuthError()) }
    }

    // ── Google ───────────────────────────────────────────────────────────────
    override fun signInWithGoogle(activity: Activity, onSuccess: (AccountUser) -> Unit, onError: (AuthError) -> Unit) {
        // Generated by the google-services plugin once Google sign-in is enabled in the Firebase console.
        val resId = activity.resources.getIdentifier("default_web_client_id", "string", activity.packageName)
        if (resId == 0) {
            onError(AuthError(AuthErrorReason.NOT_CONFIGURED, "default_web_client_id missing"))
            return
        }
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(activity.getString(resId))
            .setFilterByAuthorizedAccounts(false)     // false = also offer accounts that never signed in here (sign-up)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        CredentialManager.create(activity).getCredentialAsync(
            activity, request, CancellationSignal(), ContextCompat.getMainExecutor(activity),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        try {
                            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                            firebaseWithGoogleToken(token, onSuccess, onError)
                        } catch (e: GoogleIdTokenParsingException) {
                            onError(AuthError(AuthErrorReason.UNKNOWN, e.message))
                        }
                    } else {
                        onError(AuthError(AuthErrorReason.UNKNOWN, "Unexpected credential type"))
                    }
                }

                override fun onError(e: GetCredentialException) {
                    onError(
                        when (e) {
                            is GetCredentialCancellationException -> AuthError(AuthErrorReason.CANCELLED)
                            is NoCredentialException -> AuthError(AuthErrorReason.NO_GOOGLE_ACCOUNT)
                            else -> AuthError(AuthErrorReason.UNKNOWN, e.message)
                        }
                    )
                }
            },
        )
    }

    private fun firebaseWithGoogleToken(idToken: String, onSuccess: (AccountUser) -> Unit, onError: (AuthError) -> Unit) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { r ->
                val user = r.user
                if (user != null) onSuccess(user.toAccount()) else onError(AuthError(AuthErrorReason.UNKNOWN))
            }
            .addOnFailureListener { onError(it.toAuthError()) }
    }

    // ── Sign out ─────────────────────────────────────────────────────────────
    override fun signOut(context: Context) {
        auth.signOut()
        // Also forget the chosen Google account so the next sign-in shows the chooser again.
        try {
            CredentialManager.create(context).clearCredentialStateAsync(
                ClearCredentialStateRequest(), CancellationSignal(), ContextCompat.getMainExecutor(context),
                object : CredentialManagerCallback<Void?, ClearCredentialException> {
                    override fun onResult(result: Void?) {}
                    override fun onError(e: ClearCredentialException) {}
                },
            )
        } catch (_: Exception) { /* signing out of Firebase is what matters */ }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────
    private fun FirebaseUser.toAccount() = AccountUser(
        id = uid,
        displayName = displayName?.takeIf { it.isNotBlank() }
            ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
            ?: "User",
        email = email,
        photoUrl = photoUrl?.toString(),
    )

    private fun Throwable.toAuthError(): AuthError = when (this) {
        is FirebaseAuthUserCollisionException -> AuthError(AuthErrorReason.EMAIL_IN_USE)
        is FirebaseAuthWeakPasswordException -> AuthError(AuthErrorReason.WEAK_PASSWORD)
        is FirebaseAuthInvalidCredentialsException ->
            if (errorCode == "ERROR_INVALID_EMAIL") AuthError(AuthErrorReason.INVALID_EMAIL)
            else AuthError(AuthErrorReason.INVALID_CREDENTIALS)
        is FirebaseAuthInvalidUserException -> AuthError(AuthErrorReason.INVALID_CREDENTIALS)
        is FirebaseTooManyRequestsException -> AuthError(AuthErrorReason.TOO_MANY_ATTEMPTS)
        is FirebaseNetworkException -> AuthError(AuthErrorReason.NETWORK)
        else -> AuthError(AuthErrorReason.UNKNOWN, message)
    }
}