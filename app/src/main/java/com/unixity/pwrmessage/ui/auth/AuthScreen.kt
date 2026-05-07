package com.unixity.pwrmessage.ui.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unixity.pwrmessage.data.prefs.UserPrefs
import com.unixity.pwrmessage.data.remote.ApiService
import com.unixity.pwrmessage.data.remote.AuthRequest
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { ApiService.getInstance() }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    fun showToast(msg: String) =
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun handleAuth(mode: String) {
        if (username.isBlank() || password.isBlank()) {
            showToast("Enter credentials")
            return
        }
        isLoading = true
        scope.launch {
            try {
                val body = AuthRequest(username.trim(), password.trim())
                val resp = if (mode == "login") api.login(body) else api.register(body)

                if (resp.isSuccessful && mode == "login") {
                    val data = resp.body()!!
                    UserPrefs.saveAuth(context, data.token, data.user)
                    onAuthSuccess()
                } else if (resp.isSuccessful && mode == "register") {
                    showToast("Registered! Please login.")
                } else {
                    showToast("Auth failed")
                }
            } catch (e: Exception) {
                showToast("Server unreachable")
            }
            isLoading = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "pwRMessage",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = { handleAuth("login") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Login")
                    }

                    OutlinedButton(
                        onClick = { handleAuth("register") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Register")
                    }
                }
            }
        }
    }
}