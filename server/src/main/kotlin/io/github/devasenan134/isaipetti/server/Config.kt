package io.github.devasenan134.isaipetti.server

import java.io.File

/** Settings come from environment variables (set in the server's .env file, never in the code). */
data class Config(
    val port: Int,
    val dbPath: String,
    val navidromeUrl: String,
    val navidromeAdminUser: String,
    val navidromeAdminPassword: String,
    /** Firebase service-account key (JSON file). Without it, push notifications are off. */
    val firebaseKeyFile: String? = null,
    /** "owner/repo" for bug reports, and a token that may only open issues there. Without them, bug reports are off. */
    val githubRepo: String? = null,
    val githubToken: String? = null,
) {
    companion object {
        fun fromEnv(): Config {
            fun env(name: String, default: String? = null) =
                System.getenv(name) ?: default ?: error("Missing environment variable $name")
            return Config(
                port = env("PORT", "8095").toInt(),
                dbPath = env("DB_PATH", "data/isaipetti-social.db"),
                navidromeUrl = env("NAVIDROME_URL").trimEnd('/'),
                navidromeAdminUser = env("NAVIDROME_ADMIN_USER", ""),
                navidromeAdminPassword = env("NAVIDROME_ADMIN_PASSWORD", ""),
                firebaseKeyFile = System.getenv("FIREBASE_KEY_FILE")?.takeIf { File(it).exists() },
                githubRepo = System.getenv("GITHUB_REPO")?.takeIf { it.isNotBlank() },
                githubToken = System.getenv("GITHUB_TOKEN")?.takeIf { it.isNotBlank() },
            )
        }
    }
}
