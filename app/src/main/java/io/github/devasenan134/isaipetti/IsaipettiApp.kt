package io.github.devasenan134.isaipetti

import android.app.Application
import io.github.devasenan134.isaipetti.data.SessionStore
import io.github.devasenan134.isaipetti.data.SubsonicApi
import io.github.devasenan134.isaipetti.playback.PlayerConnection
import io.github.devasenan134.isaipetti.social.Social
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Created once when the app process starts. It holds the app-wide objects that
 * screens and the playback service share (a very small hand-made "dependency container").
 */
class IsaipettiApp : Application() {
    lateinit var session: SessionStore
        private set
    lateinit var api: SubsonicApi
        private set
    lateinit var player: PlayerConnection
        private set
    lateinit var social: Social
        private set

    override fun onCreate() {
        super.onCreate()
        session = SessionStore(this).also { it.load() }
        val http = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        api = SubsonicApi(http) { session.credentials.value }
        player = PlayerConnection(this, api)
        social = Social(session, http, BuildConfig.SOCIAL_URL)
    }
}
