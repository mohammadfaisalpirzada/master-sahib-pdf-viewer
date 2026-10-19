package com.themastersahib.pdfviewer.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Firebase email/password and Google authentication. */
object Auth {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** Is Firebase configured in this build (google-services.json present)? */
    fun isConfigured(): Boolean = try {
        FirebaseApp.getInstance(); true
    } catch (e: Exception) { false }

    val uid: String? get() = runCatching { auth.currentUser?.uid }.getOrNull()
    val email: String? get() = runCatching { auth.currentUser?.email }.getOrNull()

    suspend fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun signUp(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun signInWithCredential(cred: AuthCredential) {
        auth.signInWithCredential(cred).await()
    }

    fun signOut(context: Context? = null) {
        runCatching { auth.signOut() }
        if (context != null) {
            runCatching {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                GoogleSignIn.getClient(context, gso).signOut()
            }
        }
    }
}
