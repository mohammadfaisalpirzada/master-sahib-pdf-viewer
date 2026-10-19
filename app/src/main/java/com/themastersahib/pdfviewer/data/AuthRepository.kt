package com.themastersahib.pdfviewer.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/** Firebase email/password authentication. */
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

    suspend fun signInWithCredential(cred: AuthCredential) {
        auth.signInWithCredential(cred).await()
    }

    fun signOut() = auth.signOut()
}
