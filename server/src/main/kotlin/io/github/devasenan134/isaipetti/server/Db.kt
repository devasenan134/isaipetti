package io.github.devasenan134.isaipetti.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet

/**
 * A single SQLite file holds everything: users, sessions, invites, friends and chat.
 * For a group of friends one connection is plenty; [tx] runs one piece of work at a time.
 */
class Db(path: String) {
    private val connection: Connection

    init {
        File(path).absoluteFile.parentFile?.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:$path")
        connection.createStatement().use {
            it.execute("PRAGMA journal_mode = WAL")
            it.execute("PRAGMA foreign_keys = ON")
        }
        migrate()
    }

    /** Runs [block] inside a transaction on the IO thread pool. */
    suspend fun <T> tx(block: Connection.() -> T): T = withContext(Dispatchers.IO) {
        synchronized(connection) {
            connection.autoCommit = false
            try {
                connection.block().also { connection.commit() }
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    private fun migrate() {
        val version = connection.createStatement().use { it.executeQuery("PRAGMA user_version").run { next(); getInt(1) } }
        val migrations = listOf(SCHEMA_V1, SCHEMA_V2)
        migrations.drop(version).forEachIndexed { i, sql ->
            connection.createStatement().use { st -> sql.split(";").filter { it.isNotBlank() }.forEach(st::execute) }
            connection.createStatement().use { it.execute("PRAGMA user_version = ${version + i + 1}") }
        }
    }

    private companion object {
        val SCHEMA_V1 = """
            CREATE TABLE users (
                id INTEGER PRIMARY KEY,
                username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                display_name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE sessions (
                token_hash TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL
            );
            CREATE TABLE invites (
                code TEXT PRIMARY KEY,
                created_by INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                used_by INTEGER REFERENCES users(id),
                used_at INTEGER
            );
            CREATE TABLE friend_requests (
                from_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                to_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (from_id, to_id)
            );
            CREATE TABLE friendships (
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                friend_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                created_at INTEGER NOT NULL,
                PRIMARY KEY (user_id, friend_id)
            );
            CREATE TABLE conversations (
                id INTEGER PRIMARY KEY,
                kind TEXT NOT NULL,
                name TEXT,
                dm_key TEXT UNIQUE,
                created_by INTEGER NOT NULL REFERENCES users(id),
                created_at INTEGER NOT NULL
            );
            CREATE TABLE conversation_members (
                conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                last_read_id INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (conversation_id, user_id)
            );
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY,
                conversation_id INTEGER NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
                sender_id INTEGER NOT NULL REFERENCES users(id),
                body TEXT NOT NULL,
                song_json TEXT,
                created_at INTEGER NOT NULL
            );
            CREATE INDEX messages_by_conversation ON messages(conversation_id, id);
            CREATE TABLE devices (
                token TEXT PRIMARY KEY,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent()

        // Users removed from Navidrome are kept (renamed) so old chat messages still have a sender.
        val SCHEMA_V2 = "ALTER TABLE users ADD COLUMN deleted_at INTEGER"
    }
}

// Small helpers so queries stay short and readable.

fun Connection.update(sql: String, vararg args: Any?): Int = prepare(sql, args).use { it.executeUpdate() }

fun Connection.insert(sql: String, vararg args: Any?): Long = prepare(sql, args).use {
    it.executeUpdate()
    createStatement().use { st -> st.executeQuery("SELECT last_insert_rowid()").run { next(); getLong(1) } }
}

fun <T> Connection.query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
    prepare(sql, args).use { st -> st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } } }

fun <T> Connection.queryOne(sql: String, vararg args: Any?, map: (ResultSet) -> T): T? = query(sql, *args, map = map).firstOrNull()

private fun Connection.prepare(sql: String, args: Array<out Any?>): PreparedStatement =
    prepareStatement(sql).apply { args.forEachIndexed { i, arg -> setObject(i + 1, arg) } }

fun now(): Long = System.currentTimeMillis()
