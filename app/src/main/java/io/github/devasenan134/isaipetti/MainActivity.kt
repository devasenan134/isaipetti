package io.github.devasenan134.isaipetti

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.devasenan134.isaipetti.ui.AppRoot
import io.github.devasenan134.isaipetti.ui.theme.IsaipettiTheme

class MainActivity : ComponentActivity() {
    private val app get() = application as IsaipettiApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IsaipettiTheme {
                AppRoot(app)
            }
        }
    }

    // Connect to the playback service while the app is on screen. Music keeps playing
    // after we disconnect, because the service runs on its own.
    override fun onStart() {
        super.onStart()
        app.player.connect()
        app.social.setForeground(true)
    }

    override fun onStop() {
        app.player.disconnect()
        app.social.setForeground(false)
        super.onStop()
    }
}
