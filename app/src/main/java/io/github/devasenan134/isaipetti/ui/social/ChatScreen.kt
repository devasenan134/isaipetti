package io.github.devasenan134.isaipetti.ui.social

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.devasenan134.isaipetti.R
import io.github.devasenan134.isaipetti.data.ChatMessage
import io.github.devasenan134.isaipetti.data.SongRef
import io.github.devasenan134.isaipetti.ui.Nav
import io.github.devasenan134.isaipetti.ui.components.LocalApp
import io.github.devasenan134.isaipetti.ui.components.ScreenHeader
import kotlinx.coroutines.launch

private const val PAGE = 50

@Composable
fun ChatScreen(conversationId: Long, nav: Nav) {
    val app = LocalApp.current
    val social = app.social
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = social.me?.id
    val conversations by social.conversations.collectAsStateWithLifecycle()
    val friends by social.friends.collectAsStateWithLifecycle()
    val nowPlaying by app.player.nowPlaying.collectAsStateWithLifecycle()
    val conversation = conversations.firstOrNull { it.id == conversationId }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var hasOlder by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    fun add(message: ChatMessage) {
        if (messages.none { it.id == message.id }) messages.add(message)
    }

    // Load the latest page, then follow new messages live while this screen is open.
    LaunchedEffect(conversationId) {
        runCatching { social.api.messages(conversationId) }.onSuccess { page ->
            messages.clear()
            messages.addAll(page)
            hasOlder = page.size == PAGE
            page.lastOrNull()?.let { social.markRead(conversationId, it.id) }
        }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show() }
        social.messages.collect { if (it.conversationId == conversationId) add(it) }
    }
    DisposableEffect(conversationId) {
        social.openConversationId = conversationId
        onDispose { social.openConversationId = null }
    }
    // Jump to the newest message when one arrives (the list is drawn bottom-up).
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }

    fun send(body: String, song: SongRef? = null) {
        sending = true
        scope.launch {
            try {
                add(social.api.sendMessage(conversationId, body, song))
                if (song == null) draft = ""
                social.refreshConversationsSoon()
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Couldn't send", Toast.LENGTH_SHORT).show()
            }
            sending = false
        }
    }

    val other = conversation?.members?.firstOrNull { it.id != me }
    val otherFriend = friends.firstOrNull { it.user.id == other?.id }
    Column(Modifier.imePadding()) {
        ScreenHeader(conversation?.title(me) ?: "Chat", onBack = nav.back)
        val subtitle = when {
            conversation == null -> null
            conversation.isGroup -> conversation.members.joinToString { if (it.id == me) "You" else it.displayName }
            otherFriend?.nowPlaying != null -> "♪ Listening to ${otherFriend.nowPlaying.title}"
            otherFriend?.online == true -> "Online"
            else -> null
        }
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (otherFriend?.nowPlaying != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 60.dp, end = 16.dp, bottom = 4.dp),
            )
        }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            val newestFirst = messages.asReversed()
            itemsIndexed(newestFirst, key = { _, m -> m.id }) { i, message ->
                // Show the sender's name in groups when a new person starts talking.
                val olderNeighbour = newestFirst.getOrNull(i + 1)
                val showName = conversation?.isGroup == true && message.sender.id != me && olderNeighbour?.sender?.id != message.sender.id
                Bubble(message, mine = message.sender.id == me, showName = showName)
            }
            if (hasOlder) {
                item {
                    TextButton(onClick = {
                        scope.launch {
                            val older = runCatching { social.api.messages(conversationId, before = messages.firstOrNull()?.id) }.getOrDefault(emptyList())
                            messages.addAll(0, older.filter { o -> messages.none { it.id == o.id } })
                            hasOlder = older.size == PAGE
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Load earlier messages") }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Share whatever is playing right now with one tap.
            val current = nowPlaying.song
            IconButton(enabled = current != null && !sending, onClick = { current?.let { send("", it) } }) {
                Icon(painterResource(R.drawable.ic_music_note), contentDescription = "Share the song you're playing")
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(enabled = draft.isNotBlank() && !sending, onClick = { send(draft.trim()) }) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage, mine: Boolean, showName: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        if (showName) {
            Text(
                message.sender.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp),
            )
        }
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                message.song?.let { SongCard(it, Modifier.padding(bottom = if (message.body.isNotBlank()) 6.dp else 0.dp)) }
                if (message.body.isNotBlank()) Text(message.body, style = MaterialTheme.typography.bodyLarge)
                Text(
                    chatTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                )
            }
        }
    }
}
