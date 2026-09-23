package io.github.devasenan134.isaipetti.server

/** Settings come from environment variables (set in the server's .env file, never in the code). */
data class Config(
    val port: Int,
    val dbPath: String,
    val navidromeUrl: String,
    val navidromeAdminUser: String,
    val navidromeAdminPassword: String,
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
            )
        }
    }
}
