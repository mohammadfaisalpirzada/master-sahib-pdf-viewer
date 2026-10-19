package com.themastersahib.pdfviewer.ui

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.themastersahib.pdfviewer.data.Auth
import kotlinx.coroutines.launch

/** Email/password login & sign-up. Calls [onLoggedIn] once auth succeeds. */
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
                error = e.message ?: "Authentication failed"
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
            Text(if (isSignUp) "Create your account" else "Sign in to continue",
                style = MaterialTheme.typography.bodyMedium)
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

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text(if (isSignUp) "Sign Up" else "Sign In")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { isSignUp = !isSignUp; error = null }, enabled = !busy) {
                Text(if (isSignUp) "Already have an account? Sign In" else "New here? Create an account")
            }
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
