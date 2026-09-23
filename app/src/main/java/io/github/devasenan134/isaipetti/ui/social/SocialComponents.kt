package io.github.devasenan134.isaipetti.ui.social

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.devasenan134.isaipetti.R
import io.github.devasenan134.isaipetti.data.SongRef
import io.github.devasenan134.isaipetti.ui.components.Cover
import io.github.devasenan134.isaipetti.ui.components.LocalApp
import io.github.devasenan134.isaipetti.ui.components.SectionTitle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private val avatarColors = listOf(
    Color(0xFFF5B942), Color(0xFFE8643C), Color(0xFF7FB8A4), Color(0xFF8C7BD8),
    Color(0xFFE38FB0), Color(0xFF6FA8DC), Color(0xFFC9A26B),
)

/** A coloured circle with the person's first letter, and a green dot when they're online. */
@Composable
fun Avatar(name: String, key: String, size: Dp = 44.dp, online: Boolean = false) {
    Box {
        Box(
            Modifier.size(size).background(avatarColors[key.hashCode().absoluteValue % avatarColors.size], CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "?",
                color = Color(0xFF1B1726),
                style = if (size > 40.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
            )
        }
        if (online) {
            Box(
                Modifier.align(Alignment.BottomEnd).size(size / 3.5f)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .background(Color(0xFF4CC38A), CircleShape),
            )
        }
    }
}

/** "14:05" for today, "Mon" within a week, else "12 Sep". */
fun chatTime(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val time = Instant.ofEpochMilli(millis).atZone(zone)
    val today = LocalDate.now(zone)
    val pattern = when {
        time.toLocalDate() == today -> "HH:mm"
        time.toLocalDate().isAfter(today.minusDays(7)) -> "EEE"
        else -> "d MMM"
    }
    return time.format(DateTimeFormatter.ofPattern(pattern))
}

/** A shared song inside a chat bubble: cover, title, and a play button. */
@Composable
fun SongCard(song: SongRef, modifier: Modifier = Modifier) {
    val player = LocalApp.current.player
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = modifier.clickable { player.play(listOf(song.toSong())) },
    ) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(song.coverArt, Modifier.size(48.dp), size = 150, corner = 6.dp)
            Column(Modifier.weight(1f, fill = false).padding(horizontal = 10.dp)) {
                Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(song.artist, song.album).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { player.play(listOf(song.toSong())) }) {
                Icon(painterResource(R.drawable.ic_play), contentDescription = "Play")
            }
        }
    }
}

/** Pick a chat or friend to send [song] to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSongSheet(song: SongRef, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val social = app.social
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversations by social.conversations.collectAsStateWithLifecycle()
    val friends by social.friends.collectAsStateWithLifecycle()
    val me = social.me?.id
    // Friends you don't have a DM with yet also appear, so you can share with anyone.
    val dmPartners = conversations.filter { !it.isGroup }.flatMap { c -> c.members.map { it.id } }.toSet()
    val newPeople = friends.filter { it.user.id !in dmPartners }

    fun send(title: String, conversationId: suspend () -> Long) {
        scope.launch {
            try {
                social.api.sendMessage(conversationId(), "", song)
                Toast.makeText(context, "Sent to $title", Toast.LENGTH_SHORT).show()
                social.refreshConversationsSoon()
                onDismiss()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Couldn't send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        SectionTitle("Share \"${song.title}\"")
        if (conversations.isEmpty() && newPeople.isEmpty()) {
            Text(
                "Add friends first, from the Friends tab.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            items(conversations, key = { "c${it.id}" }) { c ->
                val title = c.title(me)
                ShareTarget(title, "c${c.id}") { send(title) { c.id } }
            }
            items(newPeople, key = { "f${it.user.id}" }) { f ->
                ShareTarget(f.user.displayName, f.user.username) { send(f.user.displayName) { social.api.openDm(f.user.id).id } }
            }
        }
    }
}

@Composable
private fun ShareTarget(title: String, key: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(title, key, size = 40.dp)
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 14.dp))
    }
}
