package io.github.devasenan134.isaipetti.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import java.io.File

/** An uploaded picture: the app sends it already cropped and shrunk (a square JPEG, about 100 KB). */
class Picture(val bytes: ByteArray) {
    val type: ContentType = when {
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> ContentType.Image.JPEG
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(PNG) -> ContentType.Image.PNG
        bytes.size >= 12 && String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WEBP" -> ContentType("image", "webp")
        else -> throw ApiError(HttpStatusCode.BadRequest, "That isn't a JPEG, PNG or WebP picture")
    }

    init {
        if (bytes.size > MAX_BYTES) throw ApiError(HttpStatusCode.PayloadTooLarge, "The picture is too big")
    }

    val extension get() = type.contentSubtype

    companion object {
        const val MAX_BYTES = 900 * 1024
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}

/** Picture files kept next to the database, one per id, in the folder [name] (e.g. "avatars"). */
class PictureFolder(dbPath: String, name: String) {
    private val dir = File(File(dbPath).absoluteFile.parentFile, name).apply { mkdirs() }

    fun save(id: Long, picture: Picture) {
        remove(id)
        // Write next to it, then swap, so nobody ever gets half a picture.
        File(dir, "$id.tmp").apply { writeBytes(picture.bytes) }.renameTo(File(dir, "$id.${picture.extension}"))
    }

    fun remove(id: Long) = files(id).forEach { it.delete() }

    /** The picture of [id], or null if there's none. */
    fun get(id: Long): File? = files(id).firstOrNull()

    private fun files(id: Long) = dir.listFiles { f -> f.nameWithoutExtension == id.toString() && f.extension != "tmp" }.orEmpty().toList()
}

/**
 * Profile pictures (kept as files next to the database, one per person) and playlist covers
 * (stored in Navidrome, which only lets a playlist's owner or an admin change them).
 */
class Pictures(private val db: Db, private val navidrome: Navidrome, dbPath: String) {
    private val avatars = PictureFolder(dbPath, "avatars")

    suspend fun setAvatar(userId: Long, picture: Picture): UserDto {
        avatars.save(userId, picture)
        return db.tx {
            update("UPDATE users SET avatar_at = ? WHERE id = ?", now(), userId)
            queryOne("SELECT * FROM users WHERE id = ?", userId) { it.toUser() }!!
        }
    }

    suspend fun removeAvatar(userId: Long): UserDto {
        avatars.remove(userId)
        return db.tx {
            update("UPDATE users SET avatar_at = NULL WHERE id = ?", userId)
            queryOne("SELECT * FROM users WHERE id = ?", userId) { it.toUser() }!!
        }
    }

    /** The picture file of [userId], or null if they have none. */
    fun avatar(userId: Long): File? = avatars.get(userId)

    /** Sets (or with null, removes) the cover of a playlist [user] made. */
    suspend fun setPlaylistCover(user: UserDto, playlistId: String, picture: Picture?) {
        val owner = navidrome.playlistOwner(playlistId) ?: throw ApiError(HttpStatusCode.NotFound, "Playlist not found")
        val me = db.tx { queryOne("SELECT navidrome_id FROM users WHERE id = ?", user.id) { it.getString(1) } }
            ?: navidrome.idFor(user.username)
        if (owner != me) throw ApiError(HttpStatusCode.Forbidden, "Only whoever made a playlist can change its cover")
        if (picture == null) navidrome.removePlaylistImage(playlistId) else navidrome.setPlaylistImage(playlistId, picture)
    }
}
