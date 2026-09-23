package io.github.devasenan134.isaipetti.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.devasenan134.isaipetti.R
import io.github.devasenan134.isaipetti.ui.home.HomeScreen
import io.github.devasenan134.isaipetti.ui.library.AlbumScreen
import io.github.devasenan134.isaipetti.ui.library.AlbumsScreen
import io.github.devasenan134.isaipetti.ui.library.ArtistScreen
import io.github.devasenan134.isaipetti.ui.library.ArtistsScreen
import io.github.devasenan134.isaipetti.ui.library.PlaylistScreen
import io.github.devasenan134.isaipetti.ui.player.MiniPlayer
import io.github.devasenan134.isaipetti.ui.player.PlayerScreen
import io.github.devasenan134.isaipetti.ui.search.SearchScreen
import kotlinx.serialization.Serializable

// Each screen is a "route". Routes with an id carry it along when navigating.
@Serializable object HomeRoute
@Serializable object AlbumsRoute
@Serializable object ArtistsRoute
@Serializable object SearchRoute
@Serializable data class AlbumRoute(val id: String)
@Serializable data class ArtistRoute(val id: String)
@Serializable data class PlaylistRoute(val id: String)

/** Navigation actions that screens can call. */
class Nav(
    val openAlbum: (String) -> Unit,
    val openArtist: (String) -> Unit,
    val openPlaylist: (String) -> Unit,
    val back: () -> Unit,
)

private data class Tab(val label: String, val route: Any, val icon: @Composable () -> Painter)

private val tabs = listOf(
    Tab("Home", HomeRoute) { rememberVectorPainter(Icons.Filled.Home) },
    Tab("Movies", AlbumsRoute) { painterResource(R.drawable.ic_album) },
    Tab("Composers", ArtistsRoute) { rememberVectorPainter(Icons.Filled.Person) },
    Tab("Search", SearchRoute) { rememberVectorPainter(Icons.Filled.Search) },
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    val nav = Nav(
        openAlbum = { navController.navigate(AlbumRoute(it)) },
        openArtist = { navController.navigate(ArtistRoute(it)) },
        openPlaylist = { navController.navigate(PlaylistRoute(it)) },
        back = { navController.popBackStack() },
    )

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    MiniPlayer(onOpen = { playerOpen = true })
                    val current by navController.currentBackStackEntryAsState()
                    NavigationBar {
                        tabs.forEach { tab ->
                            val selected = current?.destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    // Standard bottom-tab behaviour: one copy of each tab, keep its state.
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon(), contentDescription = null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
                composable<HomeRoute> { HomeScreen(nav) }
                composable<AlbumsRoute> { AlbumsScreen(nav) }
                composable<ArtistsRoute> { ArtistsScreen(nav) }
                composable<SearchRoute> { SearchScreen(nav) }
                composable<AlbumRoute> { AlbumScreen(it.toRoute<AlbumRoute>().id, nav) }
                composable<ArtistRoute> { ArtistScreen(it.toRoute<ArtistRoute>().id, nav) }
                composable<PlaylistRoute> { PlaylistScreen(it.toRoute<PlaylistRoute>().id, nav) }
            }
        }

        // The full-screen player slides up over everything.
        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            PlayerScreen(
                onClose = { playerOpen = false },
                onOpenAlbum = {
                    playerOpen = false
                    nav.openAlbum(it)
                },
            )
        }
        BackHandler(enabled = playerOpen) { playerOpen = false }
    }
}
