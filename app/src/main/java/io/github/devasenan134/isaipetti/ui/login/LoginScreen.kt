package io.github.devasenan134.isaipetti.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.devasenan134.isaipetti.data.SubsonicApi
import io.github.devasenan134.isaipetti.ui.components.LocalApp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var server by rememberSaveable { mutableStateOf("https://music.example.com") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun logIn() {
        busy = true
        error = null
        scope.launch {
            val credentials = SubsonicApi.credentialsFor(server, username, password)
            try {
                app.api.ping(credentials) // checks the server address and password
                app.session.save(credentials) // AppRoot then switches to the main screen
            } catch (e: Exception) {
                error = e.message ?: "Couldn't connect"
                busy = false
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().imePadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("இசைப்பெட்டி", fontSize = 34.sp, color = MaterialTheme.colorScheme.primary)
            Text("Isaipetti", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = server, onValueChange = { server = it }, label = { Text("Server") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = username, onValueChange = { username = it }, label = { Text("Username") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = ::logIn,
                enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (busy) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp) else Text("Log in")
            }
        }
    }
}
