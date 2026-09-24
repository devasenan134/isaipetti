package io.github.devasenan134.isaipetti.server

import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * See build.gradle.kts, task previewMixes: prints how the library's languages were worked out, and
 * every mix one person would get today with the languages in it.
 */
fun main(args: Array<String>) = runBlocking {
    val (navidromeDb, featuresDb, username) = args.toList() + List(3) { "" }
    val source = NavidromeLibrary(navidromeDb, featuresDb.ifBlank { null })
    val lib = source.snapshot() ?: error("Couldn't read $navidromeDb")
    val history = source.navidromeUserId(username)?.let { source.history(it, lib) } ?: History.EMPTY
    println("${lib.songs.size} songs, ${lib.analyzed} analyzed; $username has ${history.playCount.size} played and ${history.starred.size} liked songs")

    // Languages: how many of each, how many were tagged, and a few examples of each to check by eye.
    val languages = lib.language
    val tagged = lib.songs.count { Languages.ownEvidence(it).isNotEmpty() }
    println("\nLanguages ($tagged songs had their own clue):")
    languages.groupBy { it ?: "unknown" }.entries.sortedByDescending { it.value.size }.forEach { (language, list) ->
        val examples = lib.songs.indices.filter { (languages[it] ?: "unknown") == language }.shuffled(kotlin.random.Random(1)).take(6)
            .joinToString(" | ") { "${lib.songs[it].title} (${lib.songs[it].album})" }
        println("  $language: ${list.size}  e.g. $examples")
    }

    val playlists = source.playlists(lib)
    val maker = MixMaker(
        lib, history, emptyMap(), source.popularity(lib), emptyMap(), personSeed = username.hashCode().toLong(), today = LocalDate.now(),
        together = Together.build(emptyList(), playlists),
    )
    println("\n${playlists.size} playlists count towards which songs go together")
    for (section in maker.home()) {
        println("\n##### ${section.title}")
        for (mix in section.mixes) {
            val mixLanguages = mix.songs.mapNotNull { s -> lib.index[s.id] }.groupingBy { languages[it] ?: "unknown" }.eachCount()
                .entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" }
            println("\n== ${mix.title} (${mix.songs.size} songs: $mixLanguages) — ${mix.subtitle}")
            mix.songs.take(8).forEach { s -> println("   ${s.title} — ${s.album} (${s.year ?: "?"}) [${lib.index[s.id]?.let { languages[it] } ?: "?"}]") }
        }
    }
}
