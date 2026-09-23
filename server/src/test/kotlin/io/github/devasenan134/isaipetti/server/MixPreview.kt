package io.github.devasenan134.isaipetti.server

import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** See build.gradle.kts, task previewMixes: prints every mix one person would get today. */
fun main(args: Array<String>) = runBlocking {
    val (navidromeDb, featuresDb, username) = args.toList() + List(3) { "" }
    val source = NavidromeLibrary(navidromeDb, featuresDb.ifBlank { null })
    val lib = source.snapshot() ?: error("Couldn't read $navidromeDb")
    val history = source.navidromeUserId(username)?.let { source.history(it, lib) } ?: History.EMPTY
    println("${lib.songs.size} songs, ${lib.analyzed} analyzed; $username has ${history.playCount.size} played and ${history.starred.size} liked songs")
    val maker = MixMaker(lib, history, emptyMap(), source.popularity(lib), emptyMap(), personSeed = username.hashCode().toLong(), today = LocalDate.now())
    for (section in maker.home()) {
        println("\n##### ${section.title}")
        for (mix in section.mixes) {
            println("\n== ${mix.title} (${mix.songs.size} songs) by ${mix.author} — ${mix.subtitle}")
            mix.songs.take(8).forEach { println("   ${it.title} — ${it.album} (${it.year ?: "?"})") }
        }
    }
}
