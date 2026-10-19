package com.themastersahib.pdfviewer.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.themastersahib.pdfviewer.R
import com.themastersahib.pdfviewer.data.Auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Email/password & Google login/sign-up. Calls [onLoggedIn] once auth succeeds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPw by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val googleClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        busy = true; error = null
        scope.launch {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken
                    ?: throw IllegalStateException("Missing Google ID token. Check Web Client ID configuration.")
                Auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                onLoggedIn()
            } catch (e: ApiException) {
                error = when (e.statusCode) {
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> null
                    GoogleSignInStatusCodes.SIGN_IN_CURRENTLY_IN_PROGRESS -> null
                    10 -> "Google sign-in configuration error (code 10). Ensure app is updated to the latest release."
                    7 -> "Network error during Google sign-in. Check your internet connection."
                    else -> "Google sign-in failed (code ${e.statusCode}): ${e.message}"
                }
            } catch (e: Exception) {
                error = "Google sign-in failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun submit() {
        if (email.isBlank() || password.length < 6) {
            error = "Enter a valid email and a password of at least 6 characters."
            return
        }
        busy = true; error = null
        scope.launch {
            try {
                if (isSignUp) Auth.signUp(email, password) else Auth.signIn(email, password)
                onLoggedIn()
            } catch (e: Exception) {
                val raw = e.message ?: "Authentication failed"
                error = if (!isSignUp && (raw.contains("auth credential is incorrect", ignoreCase = true) ||
                        raw.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true))) {
                    "Incorrect email or password. If you are a new user, tap 'New here? Create an account' or 'Continue with Google' below."
                } else {
                    raw
                }
            } finally {
                busy = false
            }
        }
    }

    fun resetPassword() {
        if (email.isBlank() || !email.contains("@")) {
            error = "Enter your email address above first, then tap Forgot password."
            return
        }
        busy = true; error = null
        scope.launch {
            try {
                Auth.sendPasswordReset(email)
                Toast.makeText(context, "Password reset email sent to ${email.trim()}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                error = e.message ?: "Could not send password reset email"
            } finally {
                busy = false
            }
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text("Photostat Master", style = MaterialTheme.typography.headlineSmall)
            Text("by The Master Sahib", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (isSignUp) "Create your account" else "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") }, singleLine = true,
                visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(
                            if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showPw) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (!isSignUp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { resetPassword() }, enabled = !busy) {
                        Text("Forgot password?", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (isSignUp) "Sign Up" else "Sign In")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { isSignUp = !isSignUp; error = null }, enabled = !busy) {
                Text(if (isSignUp) "Already have an account? Sign In" else "New here? Create an account")
            }

            Spacer(Modifier.height(8.dp))
            Text("or", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    error = null
                    scope.launch {
                        runCatching { googleClient.signOut().await() }
                        googleLauncher.launch(googleClient.signInIntent)
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Continue with Google") }
        }
    }
}

/** Shown when google-services.json is missing (Firebase not set up yet). */
@Composable
fun FirebaseSetupNeededScreen() {
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Firebase not configured", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Add google-services.json (from your Firebase project) to the app module, " +
                    "then rebuild. Login & cloud sync will then be enabled.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
