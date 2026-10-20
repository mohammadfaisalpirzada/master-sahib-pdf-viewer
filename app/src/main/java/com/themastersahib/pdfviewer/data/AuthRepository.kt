package com.themastersahib.pdfviewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class UserProfileInfo(
    val uid: String,
    val email: String,
    val displayName: String,
    val photoUrl: String?,
    val providerName: String
)

/** Firebase email/password and Google authentication. */
object Auth {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val photoCache = mutableMapOf<String, Bitmap>()

    /** Is Firebase configured in this build (google-services.json present)? */
    fun isConfigured(): Boolean = try {
        FirebaseApp.getInstance(); true
    } catch (e: Exception) { false }

    val uid: String? get() = runCatching { auth.currentUser?.uid }.getOrNull()
    val email: String? get() = runCatching { auth.currentUser?.email }.getOrNull()

    fun currentProfile(): UserProfileInfo? {
        val u = runCatching { auth.currentUser }.getOrNull() ?: return null
        val mail = u.email.orEmpty()
        val name = u.displayName?.takeIf { it.isNotBlank() }
            ?: mail.substringBefore("@").replace('.', ' ').replaceFirstChar { it.uppercase() }.ifBlank { "Master Sahib User" }
        val isGoogle = u.providerData.any { it.providerId.contains("google", ignoreCase = true) }
        return UserProfileInfo(
            uid = u.uid,
            email = mail.ifBlank { "No email" },
            displayName = name,
            photoUrl = u.photoUrl?.toString(),
            providerName = if (isGoogle) "Google Account" else "Email & Password"
        )
    }

    suspend fun loadAvatarBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        photoCache[url]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { inp ->
                    BitmapFactory.decodeStream(inp)?.also { bmp -> photoCache[url] = bmp }
                }
            }.getOrNull()
        }
    }

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
