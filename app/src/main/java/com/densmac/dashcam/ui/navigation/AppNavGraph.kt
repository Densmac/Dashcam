package com.densmac.dashcam.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.densmac.dashcam.core.design.haptics.HapticEvent
import com.densmac.dashcam.core.design.haptics.LocalDashcamHapticsEnabled
import com.densmac.dashcam.core.design.haptics.hapticClickable
import com.densmac.dashcam.core.design.haptics.rememberDashcamHaptics
import com.densmac.dashcam.core.design.components.ThemeModeSwitch
import com.densmac.dashcam.data.datastore.ThemeMode
import com.densmac.dashcam.ui.screens.detail.ClipDetailScreen
import com.densmac.dashcam.ui.screens.downloads.DownloadsScreen
import com.densmac.dashcam.ui.screens.library.LibraryScreen
import com.densmac.dashcam.ui.screens.live.LiveScreen
import com.densmac.dashcam.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.launch

private data class NavItem(val label: String, val route: Route, val icon: ImageVector)

private val bottomItems = listOf(
    NavItem("Live", Route.Live, Icons.Outlined.Videocam),
    NavItem("Library", Route.Library, Icons.Outlined.VideoLibrary),
    NavItem("Downloads", Route.Downloads, Icons.Outlined.Download)
)

@Composable
fun AppNavGraph(
    hapticsEnabled: Boolean = true,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        LocalDashcamHapticsEnabled provides hapticsEnabled
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DriveDeckBackground())
        ) {
            NavHost(
                navController = navController,
                startDestination = Route.MainTabs.path
            ) {
                composable(Route.MainTabs.path) {
                    MainTabsScreen(
                        hapticsEnabled = hapticsEnabled,
                        themeMode = themeMode,
                        onThemeModeChange = onThemeModeChange,
                        onOpenSettings = { navController.navigate(Route.Settings.path) },
                        onOpenDetail = { navController.navigate(Route.ClipDetail.create(it)) }
                    )
                }
                composable(Route.Settings.path) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Route.ClipDetail.path) {
                    ClipDetailScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainTabsScreen(
    hapticsEnabled: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { bottomItems.size })
    val scope = rememberCoroutineScope()
    val haptics = rememberDashcamHaptics(hapticsEnabled)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DriveDeckBackground())
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 92.dp, bottom = 98.dp)
        ) { page ->
            when (bottomItems[page].route) {
                Route.Live -> LiveScreen(
                    isVisible = pagerState.currentPage == page,
                    onOpenLibrary = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                Route.Library -> LibraryScreen(
                    onOpenDetail = onOpenDetail,
                    // Smoothly slide to the Transfers tab when a download is queued.
                    onDownloadEnqueued = {
                        scope.launch { pagerState.animateScrollToPage(bottomItems.indexOfFirst { it.route == Route.Downloads }) }
                    }
                )
                Route.Downloads -> DownloadsScreen()
                else -> Unit
            }
        }

        DriveDeckHeader(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            onOpenSettings = {
                haptics(HapticEvent.Tick)
                onOpenSettings()
            }
        )

        DriveDeckTabs(
            selectedIndex = pagerState.currentPage,
            onSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun DriveDeckBackground(): Brush {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    return Brush.radialGradient(
        colors = if (light) {
            listOf(
                Color(0xFFFFD8AA),
                Color(0xFFF2C184),
                Color(0xFFFFF1D8),
                MaterialTheme.colorScheme.background,
                Color(0xFFFFFBF2)
            )
        } else {
            listOf(
                Color(0xFF82503A),
                Color(0xFF675734),
                Color(0xFF242417),
                MaterialTheme.colorScheme.background,
                Color(0xFF060705)
            )
        },
        radius = 1_380f
    )
}

@Composable
private fun DriveDeckHeader(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        // Small brand mark, left.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFFC5784D), MaterialTheme.colorScheme.primary))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("D", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        }

        // Centered theme switch.
        ThemeModeSwitch(
            selected = themeMode,
            onSelect = onThemeModeChange,
            modifier = Modifier.align(Alignment.Center)
        )

        // Settings, right.
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), CircleShape)
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = "Settings", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun DriveDeckTabs(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val light = MaterialTheme.colorScheme.background.luminance() > 0.5f
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(if (light) Color(0xEFFFF2DA) else Color(0xE80A0F0B))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.46f), RoundedCornerShape(36.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomItems.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        if (selected) {
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                                )
                            )
                        } else {
                            Brush.linearGradient(
                                listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surface.copy(alpha = if (light) 0.58f else 0.28f)
                                )
                            )
                        }
                    )
                    .hapticClickable { onSelected(index) }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    item.icon,
                    contentDescription = item.label,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
