@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)

package com.android.r0dumpmanager

import android.content.pm.PackageManager
import android.os.Build
import android.widget.ImageView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// The component layer below is a local R0DUMP port of FolkPatch's reusable UI model:
// Theme.kt + BottomBarDestination.kt + ExpressiveCard/ExpressiveSwitch +
// SettingsItem-style rows + WarningCard + SplicedColumnGroup.
// App-specific FolkPatch dependencies are intentionally removed, but the component
// behavior and interaction vocabulary are kept nearly identical.

fun installR0DumpComposeUi(activity: MainActivity) {
    val view = ComposeView(activity)
    activity.setContentView(view)
    view.setContent {
        var snapshot by remember { mutableStateOf(activity.getComposeSnapshot(false, "", false)) }
        var config by remember { mutableStateOf(snapshot.config.copy()) }
        var destinationName by rememberSaveable { mutableStateOf(BottomDestination.Workbench.name) }
        var languageTag by rememberSaveable { mutableStateOf(activity.currentLanguageTagFromCompose()) }
        val expandedSections = remember { mutableStateMapOf<String, Boolean>() }

        fun accept(next: MainActivity.UiSnapshot) {
            snapshot = next
            config = next.config.copy()
        }

        fun refresh(scanFiles: Boolean = false) {
            accept(activity.getComposeSnapshot(scanFiles, "", config.showSystemApps))
        }

        fun refreshApps() {
            accept(activity.refreshAppsFromCompose(config.showSystemApps))
        }

        DisposableEffect(activity) {
            val callback = Runnable {
                accept(activity.getComposeSnapshot(false, "", config.showSystemApps))
            }
            activity.setComposeRefreshCallback(callback)
            onDispose { activity.setComposeRefreshCallback(null) }
        }

        LaunchedEffect(config.showSystemApps) {
            refresh(false)
        }

        R0DumpTheme {
            AnimatedContent(
                targetState = languageTag,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                transitionSpec = {
                    fadeIn(animationSpec = tween(R0LanguageTransitionMillis, easing = R0EaseOutQuint))
                        .togetherWith(fadeOut(animationSpec = tween(R0LanguageTransitionMillis / 2, easing = R0EaseOutQuint)))
                },
                label = "R0DumpLanguage",
            ) { activeLanguageTag ->
                CompositionLocalProvider(LocalR0LanguageTag provides activeLanguageTag) {
                    R0DumpScreen(
                        snapshot = snapshot,
                        config = config,
                        destination = BottomDestination.valueOf(destinationName),
                        expandedSections = expandedSections,
                        onDestinationChange = { dest ->
                            destinationName = dest.name
                            when (dest) {
                                BottomDestination.Logs -> accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.REFRESH_LOGCAT))
                                BottomDestination.Status -> accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.REFRESH_STATUS))
                                BottomDestination.Repair -> accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.SCAN_OUTPUT))
                                else -> refresh(false)
                            }
                        },
                        onConfigChange = { next -> accept(activity.updateConfigFromCompose(next.copy())) },
                        onRefreshApps = { refreshApps() },
                        onSelectApp = { pkg -> accept(activity.selectAppFromCompose(pkg, config.showSystemApps, "")) },
                        onStart = {
                            val started = activity.runComposeAction(config.copy(), MainActivity.UiAction.START)
                            accept(started)
                            destinationName = BottomDestination.Status.name
                            accept(activity.runComposeAction(started.config.copy(), MainActivity.UiAction.REFRESH_STATUS))
                        },
                        onStop = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.STOP)) },
                        onOriginalPreset = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.ORIGINAL_PRESET)) },
                        onDynamicPreset = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.DYNAMIC_DEX_PRESET)) },
                        onDexProtectorPreset = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.DEXPROTECTOR_PRESET)) },
                        onRefreshStatus = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.REFRESH_STATUS)) },
                        onAutoRefreshStatus = { accept(activity.refreshStatusFromCompose(true)) },
                        onRefreshLogcat = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.REFRESH_LOGCAT)) },
                        onAutoRefreshLogcat = { activity.requestLogcatRefreshFromCompose(true) },
                        onLogcatFilterChange = { enabled -> accept(activity.setLogcatR0dumpOnlyFromCompose(enabled)) },
                        onClearLogcat = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.CLEAR_LOGCAT)) },
                        onDumpComplete = {
                            destinationName = BottomDestination.Repair.name
                            accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.SCAN_OUTPUT))
                        },
                        onScanOutput = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.SCAN_OUTPUT)) },
                        onRepair = { accept(activity.runComposeAction(config.copy(), MainActivity.UiAction.REPAIR_DEX)) },
                        onCycleLanguage = { languageTag = activity.cycleLanguageFromCompose() },
                    )
                }
            }
        }
    }
}

private val R0Accent = Color(0xFF4F46E5)
private val R0Background = Color(0xFFF7F9FE)
private val R0Surface = Color(0xFFFFFFFF)
private val R0SurfaceContainer = Color(0xFFEFF4FB)
private val R0Ink = Color(0xFF111827)
private val R0Muted = Color(0xFF5B687C)
private val R0Danger = Color(0xFFB91C1C)
private val R0Warning = Color(0xFFB45309)
private val R0Success = Color(0xFF057A55)
private val R0EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
private const val R0DumpVersionName = "16"
private const val R0DumpTopBarTitle = "R0DUMP 16"
private const val R0LanguageTransitionMillis = 180
private const val R0PageTransitionMillis = 220
private const val R0DockSettleMillis = 320
private val R0DockContentPadding = 0.dp
private val R0DockWithActionsContentPadding = 0.dp
private val R0DockFallbackHideDistance = 128.dp
private val R0BottomActionDockGap = 28.dp
private const val UiLanguageEnglish = "en"

@Composable
private fun R0DumpTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = R0Accent,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEEEDFF),
        onPrimaryContainer = Color(0xFF27206F),
        secondary = R0Muted,
        onSecondary = Color.White,
        background = R0Background,
        onBackground = R0Ink,
        surface = R0Surface,
        onSurface = R0Ink,
        surfaceVariant = R0SurfaceContainer,
        onSurfaceVariant = R0Muted,
        surfaceContainer = R0SurfaceContainer,
        surfaceContainerHigh = Color(0xFFE7EEF8),
        surfaceContainerHighest = Color(0xFFDCE5F1),
        outline = Color(0xFFD8E0EB),
        outlineVariant = Color(0xFFE4EAF3),
        error = R0Danger,
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

private enum class BottomDestination(
    val zhLabel: String,
    val enLabel: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Workbench("工作台", "Workbench", Icons.Filled.Home, Icons.Outlined.Home),
    Config("配置", "Config", Icons.Filled.Settings, Icons.Outlined.Settings),
    Logs("日志", "Logs", Icons.Filled.Info, Icons.Outlined.Info),
    Status("状态", "Status", Icons.Filled.Info, Icons.Outlined.Info),
    Repair("修复", "Repair", Icons.Filled.Build, Icons.Outlined.Build);

    fun previous(): BottomDestination = values().getOrElse(ordinal - 1) { this }
    fun next(): BottomDestination = values().getOrElse(ordinal + 1) { this }
}

private val BottomDestination.hasFixedBottomActions: Boolean
    get() = this == BottomDestination.Config
            || this == BottomDestination.Logs
            || this == BottomDestination.Status
            || this == BottomDestination.Repair

private val LocalR0LanguageTag = compositionLocalOf { "" }

@Composable
private fun uiText(zh: String, en: String): String {
    val selectedLanguage = LocalR0LanguageTag.current
    val languageTag = selectedLanguage.ifBlank {
        LocalContext.current.resources.configuration.locales.get(0).toLanguageTag()
    }
    return if (languageTag.startsWith(UiLanguageEnglish, ignoreCase = true)) en else zh
}

@Composable
private fun R0DumpScreen(
    snapshot: MainActivity.UiSnapshot,
    config: MainActivity.UiConfig,
    destination: BottomDestination,
    expandedSections: MutableMap<String, Boolean>,
    onDestinationChange: (BottomDestination) -> Unit,
    onConfigChange: (MainActivity.UiConfig) -> Unit,
    onRefreshApps: () -> Unit,
    onSelectApp: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOriginalPreset: () -> Unit,
    onDynamicPreset: () -> Unit,
    onDexProtectorPreset: () -> Unit,
    onRefreshStatus: () -> Unit,
    onAutoRefreshStatus: () -> Unit,
    onRefreshLogcat: () -> Unit,
    onAutoRefreshLogcat: () -> Unit,
    onLogcatFilterChange: (Boolean) -> Unit,
    onClearLogcat: () -> Unit,
    onDumpComplete: () -> Unit,
    onScanOutput: () -> Unit,
    onRepair: () -> Unit,
    onCycleLanguage: () -> Unit,
) {
    val destinations = remember { BottomDestination.values().toList() }
    val pagerState = rememberPagerState(initialPage = destination.ordinal) { destinations.size }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val fallbackHideDistancePx = with(density) { R0DockFallbackHideDistance.toPx() }
    val bottomOverlayHideExtraPx = with(density) { R0BottomActionDockGap.toPx() }
    var bottomDockHeightPx by remember { mutableStateOf(0f) }
    var bottomActionHeightPx by remember { mutableStateOf(0f) }
    val dockHideDistancePx = max(fallbackHideDistancePx, bottomDockHeightPx + bottomOverlayHideExtraPx)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    var dockHidden by rememberSaveable { mutableStateOf(false) }
    var dockDragging by remember { mutableStateOf(false) }
    var dockDragOffsetPx by rememberSaveable { mutableStateOf(0f) }
    val dockTargetOffsetPx = when {
        dockDragging -> dockDragOffsetPx
        dockHidden -> dockHideDistancePx
        else -> 0f
    }
    val dockOffsetPx by animateFloatAsState(
        targetValue = dockTargetOffsetPx,
        animationSpec = if (dockDragging) {
            snap()
        } else {
            tween(R0DockSettleMillis, easing = R0EaseOutQuint)
        },
        label = "R0DockOffset",
    )
    val currentDockOffsetPx by rememberUpdatedState(dockOffsetPx)
    val dockerGestureModifier = Modifier.pointerInput(dockHideDistancePx, touchSlop) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var lastPosition = down.position
            var accumulated = Offset.Zero
            var gestureOffset = currentDockOffsetPx.coerceIn(0f, dockHideDistancePx)
            var verticalDockGesture = false
            var horizontalPagerGesture = false
            var lastDeltaY = 0f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (verticalDockGesture) {
                        dockHidden = when {
                            lastDeltaY < -4f -> true
                            lastDeltaY > 4f -> false
                            else -> gestureOffset > dockHideDistancePx * 0.45f
                        }
                        dockDragging = false
                    }
                    break
                }

                val delta = change.position - lastPosition
                lastPosition = change.position
                if (!verticalDockGesture && !horizontalPagerGesture) {
                    accumulated += delta
                    val absX = abs(accumulated.x)
                    val absY = abs(accumulated.y)
                    if (absX > touchSlop || absY > touchSlop) {
                        if (absY > absX * 1.15f) {
                            verticalDockGesture = true
                            dockDragging = true
                            dockDragOffsetPx = gestureOffset
                        } else {
                            horizontalPagerGesture = true
                        }
                    }
                }

                if (verticalDockGesture) {
                    lastDeltaY = delta.y
                    gestureOffset = (gestureOffset - delta.y).coerceIn(0f, dockHideDistancePx)
                    dockDragOffsetPx = gestureOffset
                    // Do not consume the scroll event here. The root gesture only observes vertical
                    // movement so the floating dock can track the finger while LazyColumn keeps
                    // normal vertical scrolling. Consuming this event makes long config/repair
                    // pages feel frozen.
                }
            }
        }
    }
    val currentDestination = destinations.getOrElse(pagerState.currentPage) { destination }
    val actionDockGapPx = with(density) { R0BottomActionDockGap.toPx() }
    val actionBottomPaddingPx = bottomDockHeightPx + actionDockGapPx
    val actionBottomPaddingDp = with(density) { actionBottomPaddingPx.toDp() }
    val dockProgress = if (dockHideDistancePx > 0f) {
        (dockOffsetPx / dockHideDistancePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val actionOffsetPx = actionBottomPaddingPx * dockProgress
    val dockVisibleHeightPx = (bottomDockHeightPx - dockOffsetPx).coerceIn(0f, bottomDockHeightPx)
    val actionFooterHeightPx = bottomActionHeightPx.coerceAtLeast(with(density) { 72.dp.toPx() })
    val actionsVisibleHeightPx = actionFooterHeightPx + actionBottomPaddingPx * (1f - dockProgress)
    val dockContentPaddingDp = with(density) { dockVisibleHeightPx.toDp() } + 12.dp
    val dockWithActionsContentPaddingDp = with(density) { actionsVisibleHeightPx.toDp() } + 12.dp

    LaunchedEffect(currentDestination) {
        if (!currentDestination.hasFixedBottomActions) {
            bottomActionHeightPx = 0f
        }
    }

    LaunchedEffect(destination) {
        if (pagerState.currentPage != destination.ordinal) {
            pagerState.animateScrollToPage(
                destination.ordinal,
                animationSpec = tween(R0PageTransitionMillis, easing = R0EaseOutQuint),
            )
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val settledDestination = destinations.getOrElse(pagerState.settledPage) { destination }
        if (settledDestination != destination) {
            onDestinationChange(settledDestination)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        R0DumpAppIcon()
                        Spacer(Modifier.width(10.dp))
                        Text(R0DumpTopBarTitle, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onCycleLanguage) {
                        Icon(Icons.Filled.Language, contentDescription = uiText("语言", "Language"))
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(MaterialTheme.colorScheme.background)
                .then(dockerGestureModifier),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val target = destinations.getOrElse(page) { BottomDestination.Workbench }
                val bottomContentPadding = if (target.hasFixedBottomActions) {
                    maxOf(R0DockWithActionsContentPadding, dockWithActionsContentPaddingDp)
                } else {
                    maxOf(R0DockContentPadding, dockContentPaddingDp)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomContentPadding),
                ) {
                    R0DestinationPage(
                        target = target,
                        active = target == currentDestination,
                        snapshot = snapshot,
                        config = config,
                        expandedSections = expandedSections,
                        bottomContentPadding = 24.dp,
                        onConfigChange = onConfigChange,
                        onRefreshApps = onRefreshApps,
                        onSelectApp = onSelectApp,
                        onOriginalPreset = onOriginalPreset,
                        onDynamicPreset = onDynamicPreset,
                        onDexProtectorPreset = onDexProtectorPreset,
                        onAutoRefreshStatus = onAutoRefreshStatus,
                        onAutoRefreshLogcat = onAutoRefreshLogcat,
                        onLogcatFilterChange = onLogcatFilterChange,
                        onDumpComplete = onDumpComplete,
                        onLogPageSwipe = { pageDelta ->
                            val targetPage = (pagerState.currentPage + pageDelta)
                                .coerceIn(0, destinations.lastIndex)
                            if (targetPage != pagerState.currentPage) {
                                coroutineScope.launch {
                                    dockHidden = false
                                    dockDragging = false
                                    pagerState.animateScrollToPage(
                                        targetPage,
                                        animationSpec = tween(R0PageTransitionMillis, easing = R0EaseOutQuint),
                                    )
                                }
                            }
                        },
                    )
                }
            }
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = actionBottomPaddingDp)
                    .offset { IntOffset(0, actionOffsetPx.roundToInt()) }
                    .zIndex(2f),
            ) {
                R0BottomActionFooter(
                    target = currentDestination,
                    config = config,
                    snapshot = snapshot,
                    modifier = Modifier.onGloballyPositioned { bottomActionHeightPx = it.size.height.toFloat() },
                    onStart = onStart,
                    onStop = onStop,
                    onRefreshStatus = onRefreshStatus,
                    onRefreshLogcat = onRefreshLogcat,
                    onClearLogcat = onClearLogcat,
                    onScanOutput = onScanOutput,
                    onRepair = onRepair,
                )
            }
            R0BottomBar(
                current = currentDestination,
                onDestinationChange = { target ->
                    if (target != currentDestination) {
                        coroutineScope.launch {
                            dockHidden = false
                            dockDragging = false
                            pagerState.animateScrollToPage(
                                target.ordinal,
                                animationSpec = tween(R0PageTransitionMillis, easing = R0EaseOutQuint),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, dockOffsetPx.roundToInt()) }
                    .zIndex(1f),
                onMeasuredHeight = { bottomDockHeightPx = it.toFloat() },
            )
        }
    }
}

@Composable
private fun R0DestinationPage(
    target: BottomDestination,
    active: Boolean,
    snapshot: MainActivity.UiSnapshot,
    config: MainActivity.UiConfig,
    expandedSections: MutableMap<String, Boolean>,
    bottomContentPadding: Dp,
    onConfigChange: (MainActivity.UiConfig) -> Unit,
    onRefreshApps: () -> Unit,
    onSelectApp: (String) -> Unit,
    onOriginalPreset: () -> Unit,
    onDynamicPreset: () -> Unit,
    onDexProtectorPreset: () -> Unit,
    onAutoRefreshStatus: () -> Unit,
    onAutoRefreshLogcat: () -> Unit,
    onLogcatFilterChange: (Boolean) -> Unit,
    onDumpComplete: () -> Unit,
    onLogPageSwipe: (Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
            when (target) {
                BottomDestination.Workbench -> item {
                    WorkbenchPage(
                        snapshot = snapshot,
                        config = config,
                        onConfigChange = onConfigChange,
                        expandedSections = expandedSections,
                        onRefreshApps = onRefreshApps,
                        onSelectApp = onSelectApp,
                    )
                }
                BottomDestination.Config -> item {
                    ConfigPage(
                        config = config,
                        expandedSections = expandedSections,
                        onConfigChange = onConfigChange,
                        onOriginalPreset = onOriginalPreset,
                        onDynamicPreset = onDynamicPreset,
                        onDexProtectorPreset = onDexProtectorPreset,
                    )
                }
                BottomDestination.Logs -> item {
                    LogcatPage(
                        snapshot,
                        active,
                        onAutoRefreshLogcat,
                        onLogcatFilterChange,
                        onLogPageSwipe,
                    )
                }
                BottomDestination.Status -> item { StatusPage(snapshot, onAutoRefreshStatus, onDumpComplete) }
                BottomDestination.Repair -> item { RepairPage(snapshot) }
            }
    }
}

@Composable
private fun R0BottomActionFooter(
    target: BottomDestination,
    config: MainActivity.UiConfig,
    snapshot: MainActivity.UiSnapshot,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRefreshStatus: () -> Unit,
    onRefreshLogcat: () -> Unit,
    onClearLogcat: () -> Unit,
    onScanOutput: () -> Unit,
    onRepair: () -> Unit,
) {
    when (target) {
        BottomDestination.Config -> DumpControlFooter(
            config = config,
            snapshot = snapshot,
            onStart = onStart,
            onStop = onStop,
            modifier = modifier,
        )
        BottomDestination.Logs -> LogcatActionFooter(
            snapshot = snapshot,
            onRefreshLogcat = onRefreshLogcat,
            onClearLogcat = onClearLogcat,
            modifier = modifier,
        )
        BottomDestination.Status -> StatusActionFooter(
            snapshot = snapshot,
            onRefreshStatus = onRefreshStatus,
            onScanOutput = onScanOutput,
            modifier = modifier,
        )
        BottomDestination.Repair -> RepairActionFooter(
            snapshot = snapshot,
            onScanOutput = onScanOutput,
            onRepair = onRepair,
            modifier = modifier,
        )
        BottomDestination.Workbench -> Unit
    }
}

@Composable
private fun R0DumpAppIcon() {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                setImageResource(R.drawable.ic_r0dump)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                adjustViewBounds = true
            }
        },
        modifier = Modifier.size(32.dp),
    )
}

@Composable
private fun R0BottomBar(
    current: BottomDestination,
    onDestinationChange: (BottomDestination) -> Unit,
    modifier: Modifier = Modifier,
    onMeasuredHeight: (Int) -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 12.dp)
            .onGloballyPositioned { onMeasuredHeight(it.size.height) },
    ) {
        NavigationBar(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            BottomDestination.entries.forEach { dest ->
                val selected = current == dest
                NavigationBarItem(
                    selected = selected,
                    onClick = { onDestinationChange(dest) },
                    icon = { Icon(if (selected) dest.selectedIcon else dest.unselectedIcon, null) },
                    label = { Text(uiText(dest.zhLabel, dest.enLabel)) },
                )
            }
        }
    }
}

@Composable
private fun FixedActionFooter(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
private fun DumpControlFooter(
    config: MainActivity.UiConfig,
    snapshot: MainActivity.UiSnapshot,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FixedActionFooter(modifier) {
        Button(
            onClick = onStart,
            enabled = (config.targetPackage.isNotBlank() || config.globalRuntime) && !snapshot.actionRunning,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(if (snapshot.actionRunning) uiText("启动中", "Starting") else uiText("开始dump", "Start dump"))
        }
        OutlinedButton(
            onClick = onStop,
            enabled = snapshot.dumpEnabled,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = R0Danger),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.Stop, null)
            Spacer(Modifier.width(8.dp))
            Text(uiText("停止dump", "Stop dump"))
        }
    }
}

@Composable
private fun LogcatActionFooter(
    snapshot: MainActivity.UiSnapshot,
    onRefreshLogcat: () -> Unit,
    onClearLogcat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FixedActionFooter(modifier) {
        Button(onClick = onRefreshLogcat, enabled = !snapshot.logcatRunning, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(if (snapshot.logcatRunning) uiText("刷新中", "Refreshing") else uiText("刷新日志", "Refresh logs"))
        }
        OutlinedButton(onClick = onClearLogcat, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Close, null)
            Spacer(Modifier.width(8.dp))
            Text(uiText("清空显示", "Clear view"))
        }
    }
}

@Composable
private fun StatusActionFooter(
    snapshot: MainActivity.UiSnapshot,
    onRefreshStatus: () -> Unit,
    onScanOutput: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FixedActionFooter(modifier) {
        Button(onClick = onRefreshStatus, enabled = !snapshot.statusRefreshRunning, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(uiText("刷新状态", "Refresh status"))
        }
        OutlinedButton(onClick = onScanOutput, enabled = !snapshot.scanRunning, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Folder, null)
            Spacer(Modifier.width(8.dp))
            Text(if (snapshot.scanRunning) uiText("扫描中", "Scanning") else uiText("扫描产物", "Scan outputs"))
        }
    }
}

@Composable
private fun RepairActionFooter(
    snapshot: MainActivity.UiSnapshot,
    onScanOutput: () -> Unit,
    onRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FixedActionFooter(modifier) {
        OutlinedButton(onClick = onScanOutput, enabled = !snapshot.scanRunning, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text(if (snapshot.scanRunning) uiText("扫描中", "Scanning") else uiText("扫描产物", "Scan outputs"))
        }
        Button(
            onClick = onRepair,
            enabled = !snapshot.repairRunning && snapshot.selectedPackage.isNotBlank() && snapshot.dexCount > 0 && snapshot.recordCount > 0,
            modifier = Modifier.weight(1f),
        ) { Text(if (snapshot.repairRunning) uiText("修复中", "Repairing") else uiText("修复 DEX", "Repair DEX")) }
    }
}

@Composable
private fun WorkbenchPage(
    snapshot: MainActivity.UiSnapshot,
    config: MainActivity.UiConfig,
    onConfigChange: (MainActivity.UiConfig) -> Unit,
    expandedSections: MutableMap<String, Boolean>,
    onRefreshApps: () -> Unit,
    onSelectApp: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TargetPickerCard(snapshot, config, onConfigChange, expandedSections, onRefreshApps, onSelectApp)
        R0DumpVersionCard()
        DisclaimerCard()
    }
}

@Composable
private fun R0DumpVersionCard() {
    SplicedColumnGroup(title = uiText("R0DUMP 版本", "R0DUMP version")) {
        item("manager-version") {
            MetricRow(uiText("版本", "Version"), R0DumpVersionName)
        }
        item("build-display") {
            MetricRow(uiText("系统构建", "System build"), Build.DISPLAY.ifBlank { "unknown" })
        }
        item("android-version") {
            MetricRow("Android", Build.VERSION.RELEASE, "SDK ${Build.VERSION.SDK_INT}")
        }
    }
}

@Composable
private fun DisclaimerCard() {
    SplicedColumnGroup(title = uiText("免责声明", "Disclaimer")) {
        item("flash-risk") {
            ListItem(
                headlineContent = { Text(uiText("谨慎刷机", "Flash carefully")) },
                supportingContent = { Text(uiText("变砖不负责。", "Bricked devices are your responsibility.")) },
                leadingContent = { Icon(Icons.Filled.Warning, null, tint = R0Warning) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        item("authorized-targets") {
            ListItem(
                headlineContent = { Text(uiText("仅用于授权目标", "Authorized targets only")) },
                supportingContent = { Text(uiText("请只分析你拥有权利或已获得明确许可的应用与测试环境。", "Only analyze apps and test environments you own or are explicitly allowed to inspect.")) },
                leadingContent = { Icon(Icons.Filled.Warning, null, tint = R0Warning) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        item("sensitive-output") {
            ListItem(
                headlineContent = { Text(uiText("产物可能包含敏感内容", "Outputs may contain sensitive data")) },
                supportingContent = { Text(uiText("dump 文件、method records 和修复产物可能包含私有代码或数据，请谨慎保存与分享。", "Dump files, method records, and repaired outputs may contain private code or data. Store and share them carefully.")) },
                leadingContent = { Icon(Icons.Filled.Folder, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun TargetPickerCard(
    snapshot: MainActivity.UiSnapshot,
    config: MainActivity.UiConfig,
    onConfigChange: (MainActivity.UiConfig) -> Unit,
    expandedSections: MutableMap<String, Boolean>,
    onRefreshApps: () -> Unit,
    onSelectApp: (String) -> Unit,
) {
    val controlsKey = "workbench.appSelector"
    val controlsExpanded = expandedSections[controlsKey] ?: false
    var appMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val appSelectionEnabled = !config.globalRuntime

    fun setControlsExpanded(expanded: Boolean) {
        if (expanded && !controlsExpanded) {
            onRefreshApps()
        }
        if (!expanded) {
            appMenuExpanded = false
        }
        expandedSections[controlsKey] = expanded
    }

    SplicedColumnGroup(title = uiText("应用选择", "App selection")) {
        item("target-picker") {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(role = Role.Button) { setControlsExpanded(!controlsExpanded) }
                    .padding(vertical = 2.dp),
            ) {
                TargetHeaderContent(snapshot, config, collapsed = !controlsExpanded, modifier = Modifier.weight(1f))
                IconButton(onClick = { setControlsExpanded(!controlsExpanded) }) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (controlsExpanded) {
                            uiText("收起应用选择", "Collapse app selection")
                        } else {
                            uiText("展开应用选择", "Expand app selection")
                        },
                        modifier = Modifier.rotate(if (controlsExpanded) 180f else 0f),
                    )
                }
            }
            AnimatedVisibility(visible = controlsExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        CompactToggleCard(
                            title = uiText("显示系统应用", "Show system apps"),
                            checked = config.showSystemApps,
                            modifier = Modifier.fillMaxWidth(),
                            onCheckedChange = { checked ->
                                expandedSections[controlsKey] = true
                                appMenuExpanded = false
                                onConfigChange(config.mutate { showSystemApps = checked })
                            },
                        )
                    }
                    AppDropdownSelector(
                        snapshot = snapshot,
                        config = config,
                        enabled = appSelectionEnabled,
                        expanded = appMenuExpanded && appSelectionEnabled,
                        onExpandedChange = { expanded -> appMenuExpanded = expanded && appSelectionEnabled },
                        onRefreshApps = onRefreshApps,
                        onSelectApp = { pkg ->
                            appMenuExpanded = false
                            onSelectApp(pkg)
                        },
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun TargetHeaderContent(
    snapshot: MainActivity.UiSnapshot,
    config: MainActivity.UiConfig,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
) {
    when {
        collapsed && config.globalRuntime -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Apps, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = uiText("全局模式", "Global mode"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        collapsed && config.targetPackage.isNotBlank() -> {
            AppOptionContent(
                label = snapshot.selectedLabel.ifBlank { config.targetPackage },
                packageName = config.targetPackage,
                appPackageName = config.targetPackage,
                modifier = modifier,
            )
        }
        collapsed -> {
            AppOptionContent(
                label = uiText("未选择应用", "No app selected"),
                packageName = "",
                appPackageName = "",
                modifier = modifier,
            )
        }
        else -> {
            Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Apps, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = uiText("选择目标App", "Select target app"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompactToggleCard(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        ToggleIndicator(checked = checked, enabled = enabled)
    }
}

@Composable
private fun ToggleIndicator(checked: Boolean, enabled: Boolean) {
    val targetTrackColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        checked -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val targetThumbColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
    val targetIconColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val trackColor by animateColorAsState(
        targetValue = targetTrackColor,
        animationSpec = tween(durationMillis = 180, easing = R0EaseOutQuint),
        label = "ToggleTrackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = targetThumbColor,
        animationSpec = tween(durationMillis = 180, easing = R0EaseOutQuint),
        label = "ToggleThumbColor",
    )
    val iconColor by animateColorAsState(
        targetValue = targetIconColor,
        animationSpec = tween(durationMillis = 180, easing = R0EaseOutQuint),
        label = "ToggleIconColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 180, easing = R0EaseOutQuint),
        label = "ToggleThumbOffset",
    )
    Box(
        modifier = Modifier
            .width(54.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(trackColor),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(4.dp)
                .offset(x = thumbOffset)
                .size(26.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(thumbColor),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 90))
                },
                label = "ToggleIcon",
            ) { isChecked ->
                Icon(
                    imageVector = if (isChecked) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AppDropdownSelector(
    snapshot: MainActivity.UiSnapshot,
    config: MainActivity.UiConfig,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onRefreshApps: () -> Unit,
    onSelectApp: (String) -> Unit,
) {
    val selectedApp = snapshot.apps.firstOrNull { it.packageName == config.targetPackage }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                onRefreshApps()
                onExpandedChange(true)
            },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            if (enabled) {
                AppOptionContent(
                    label = selectedApp?.label ?: snapshot.selectedLabel.ifBlank { uiText("未选择应用", "No app selected") },
                    packageName = config.targetPackage,
                    appPackageName = selectedApp?.packageName ?: config.targetPackage,
                    modifier = Modifier.weight(1f),
                )
            } else {
                AppOptionContent(
                    label = uiText("全局模式已开启", "Global mode enabled"),
                    packageName = uiText("App 选择已禁用", "App selection disabled"),
                    appPackageName = "",
                    modifier = Modifier.weight(1f),
                )
            }
            Icon(Icons.Filled.KeyboardArrowDown, null, modifier = Modifier.rotate(if (expanded) 180f else 0f))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            snapshot.apps.forEach { app ->
                DropdownMenuItem(
                    enabled = app.packageName.isNotBlank(),
                    text = {
                        AppOptionContent(
                            label = app.label,
                            packageName = app.packageName,
                            appPackageName = app.packageName,
                            selected = app.packageName == config.targetPackage,
                        )
                    },
                    onClick = { if (app.packageName.isNotBlank()) onSelectApp(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppOptionContent(
    label: String,
    packageName: String,
    appPackageName: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(appPackageName, label)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label.ifBlank { uiText("未命名应用", "Unnamed app") },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = packageName.ifBlank { uiText("无", "None") },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AppIcon(packageName: String, fallbackLabel: String) {
    if (packageName.isBlank()) {
        GenericAppIcon()
        return
    }
    val context = LocalContext.current
    val drawable = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
    if (drawable == null) {
        AppAvatar(fallbackLabel)
        return
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    adjustViewBounds = true
                }
            },
            update = { imageView -> imageView.setImageDrawable(drawable) },
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
        )
    }
}

@Composable
private fun GenericAppIcon() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Apps,
            null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CollapsibleSplicedGroup(
    title: String,
    modifier: Modifier = Modifier,
    stateKey: String = title,
    expandedSections: MutableMap<String, Boolean>? = null,
    initialExpanded: Boolean = false,
    content: SplicedGroupScope.() -> Unit,
) {
    var localExpanded by rememberSaveable(stateKey) { mutableStateOf(initialExpanded) }
    val expanded = expandedSections?.get(stateKey) ?: localExpanded
    fun setExpanded(value: Boolean) {
        if (expandedSections != null) {
            expandedSections[stateKey] = value
        } else {
            localExpanded = value
        }
    }
    Column(
        modifier = modifier.animateContentSize(
            animationSpec = tween(R0PageTransitionMillis, easing = R0EaseOutQuint),
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            trailingContent = {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(role = Role.Button) { setExpanded(!expanded) },
        )
        AnimatedVisibility(visible = expanded) {
            SplicedColumnGroup { content() }
        }
    }
}

@Composable
private fun ConfigPage(
    config: MainActivity.UiConfig,
    expandedSections: MutableMap<String, Boolean>,
    onConfigChange: (MainActivity.UiConfig) -> Unit,
    onOriginalPreset: () -> Unit,
    onDynamicPreset: () -> Unit,
    onDexProtectorPreset: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("配置", "Config"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        CollapsibleSplicedGroup(title = uiText("输出与时机", "Output and timing"), stateKey = "config.outputTiming", expandedSections = expandedSections) {
            item("output") { TextFieldItem(uiText("输出目录", "Output directory"), config.outputRoot) { onConfigChange(config.mutate { outputRoot = it }) } }
            item("delay") { NumberFieldItem(uiText("延迟 ms", "Delay ms"), config.delayMs) { onConfigChange(config.mutate { delayMs = it }) } }
            item("anrProtection") {
                SwitchItem(
                    null,
                    uiText("目标 ANR 保护", "Target ANR protection"),
                    uiText(
                        "仅在 r0dump 启用且目标包匹配时跳过系统 ANR kill，避免 dump 期间被杀。",
                        "Suppress system ANR kill only when r0dump is enabled and the target package matches.",
                    ),
                    config.anrProtectionEnabled,
                ) { onConfigChange(config.mutate { anrProtectionEnabled = it }) }
            }
        }
        CollapsibleSplicedGroup(title = uiText("原版默认路径", "Original defaults"), stateKey = "config.defaults", expandedSections = expandedSections) {
            item("presets") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onOriginalPreset, modifier = Modifier.weight(1f)) { Text(uiText("应用原版默认", "Use original defaults")) }
                        OutlinedButton(onClick = onDynamicPreset, modifier = Modifier.weight(1f)) { Text(uiText("应用动态 dex", "Use dynamic dex")) }
                    }
                    Button(onClick = onDexProtectorPreset, modifier = Modifier.fillMaxWidth()) {
                        Text(uiText("应用 DexProtector 模式", "Use DexProtector mode"))
                    }
                }
            }
            item("activity") { StrategySwitch(uiText("Activity 创建", "Activity create"), uiText("目标 Activity 创建后触发，适合多数正常启动路径。", "Trigger after target Activity creation. Good for normal launch paths."), MainActivity.STRATEGY_ACTIVITY_CREATE, config, onConfigChange) }
            item("classwalk") { StrategySwitch("Class walk", uiText("遍历已加载/可加载类，生成 method records。", "Walk loaded/loadable classes and generate method records."), MainActivity.STRATEGY_CLASS_WALK, config, onConfigChange) }
            item("methods") { SwitchItem(null, uiText("抽取普通方法", "Dump normal methods"), uiText("写出普通方法 code_item。", "Write code_item for normal methods."), config.dumpMethods) { onConfigChange(config.mutate { dumpMethods = it }) } }
            item("ctors") { SwitchItem(null, uiText("抽取构造函数", "Dump constructors"), uiText("同时抽取 direct/constructor，便于修复完整 class。", "Also dump direct/constructor methods for complete class repair."), config.dumpConstructors) { onConfigChange(config.mutate { dumpConstructors = it }) } }
            item("stopAfter") { SwitchItem(null, uiText("完成后停止热路径", "Stop hot path after completion"), uiText("类遍历完成后关闭持续 dump 开销。", "Disable continuous dump overhead after class walk completes."), config.stopAfterComplete) { onConfigChange(config.mutate { stopAfterComplete = it }) } }
        }
        CollapsibleSplicedGroup(title = uiText("范围限制", "Scope limits"), stateKey = "config.scope", expandedSections = expandedSections) {
            item("process") { TextFieldItem(uiText("目标进程", "Target process"), config.targetProcess, uiText("空为主进程，* 为匹配全部", "Empty = main process, * = match all"), alwaysFloatLabel = true) { onConfigChange(config.mutate { targetProcess = it }) } }
            item("prefix") { TextFieldItem(uiText("类名前缀", "Class prefix"), config.classPrefix, uiText("例如 com.example.", "For example com.example."), alwaysFloatLabel = true) { onConfigChange(config.mutate { classPrefix = it }) } }
            item("maxMethods") { NumberFieldItem(uiText("最大方法数", "Max methods"), config.maxMethods) { onConfigChange(config.mutate { maxMethods = it }) } }
            item("maxRecords") { NumberFieldItem(uiText("最大记录数", "Max records"), config.maxRecords) { onConfigChange(config.mutate { maxRecords = it }) } }
            item("maxSeconds") { NumberFieldItem(uiText("最大秒数", "Max seconds"), config.maxSeconds) { onConfigChange(config.mutate { maxSeconds = it }) } }
            item("walkMode") {
                OptionSelectItem(
                    title = uiText("Class walk 模式", "Class walk mode"),
                    value = config.classWalkMode,
                    options = listOf(
                        "load_all" to uiText("加载并遍历可达类", "Load and walk reachable classes"),
                        "loaded_only" to uiText("只遍历已加载类", "Walk loaded classes only"),
                    ),
                ) { onConfigChange(config.mutate { classWalkMode = it }) }
            }
            item("artClassLoaders") {
                SwitchItem(
                    null,
                    uiText("ART ClassLoader 枚举", "ART ClassLoader scan"),
                    uiText(
                        "从 ART 已注册 ClassLoader 列表补齐壳替换或自定义加载器，默认开启。",
                        "Merge ART-registered ClassLoaders for shell-swapped or custom loaders. On by default.",
                    ),
                    config.artClassLoaderScanEnabled,
                ) { onConfigChange(config.mutate { artClassLoaderScanEnabled = it }) }
            }
            item("loadedClassTable") {
                SwitchItem(
                    null,
                    uiText("已加载类表补扫", "Loaded class-table scan"),
                    uiText(
                        "对每个 ClassLoader 额外遍历 ART 已加载类名，补齐 dexElements 看不到的类。",
                        "For each ClassLoader, additionally walk ART-loaded class names missing from dexElements.",
                    ),
                    config.loadedClassTableScanEnabled,
                ) { onConfigChange(config.mutate { loadedClassTableScanEnabled = it }) }
            }
            item("manifestSeed") {
                SwitchItem(
                    null,
                    uiText("Manifest 组件 seed", "Manifest component seed"),
                    uiText(
                        "从 Activity/Service/Receiver/Provider 组件名反向尝试加载并补 dump，默认开启。",
                        "Seed class-walk from Activity/Service/Receiver/Provider names and dump matches. On by default.",
                    ),
                    config.manifestComponentSeedEnabled,
                ) { onConfigChange(config.mutate { manifestComponentSeedEnabled = it }) }
            }
            item("processMode") {
                OptionSelectItem(
                    title = uiText("进程模式", "Process mode"),
                    value = config.processMode,
                    options = listOf(
                        "main_only" to uiText("只采集主进程", "Main process only"),
                        "all" to uiText("采集全部进程", "All processes"),
                    ),
                ) { onConfigChange(config.mutate { processMode = it }) }
            }
        }
        StrategyGroups(config, onConfigChange, expandedSections)
    }
}

@Composable
private fun StrategyGroups(
    config: MainActivity.UiConfig,
    onConfigChange: (MainActivity.UiConfig) -> Unit,
    expandedSections: MutableMap<String, Boolean>,
) {
    CollapsibleSplicedGroup(title = uiText("实验策略", "Experimental strategies"), stateKey = "config.strategy", expandedSections = expandedSections) {
        item("trigger") {
            SettingsCategory(title = uiText("触发策略", "Trigger strategies"), summary = uiText("启动和生命周期入口。", "Startup and lifecycle entry points."), stateKey = "strategy.trigger", expandedSections = expandedSections) {
                StrategyOptionRows(TRIGGER_OPTIONS, config, onConfigChange)
            }
        }
        item("dexopen") {
            SettingsCategory(title = uiText("DEX 打开点", "DEX open points"), summary = uiText("针对动态 dex、oat/vdex 和 Java 路由。", "For dynamic dex, oat/vdex, and Java routes."), stateKey = "strategy.dexOpen", expandedSections = expandedSections) {
                StrategyOptionRows(DEX_OPEN_OPTIONS, config, onConfigChange)
            }
        }
        item("lifecycle") {
            SettingsCategory(title = uiText("类生命周期", "Class lifecycle"), summary = "Load/Define/Verify/Class init.", stateKey = "strategy.lifecycle", expandedSections = expandedSections) {
                StrategyOptionRows(CLASS_LIFECYCLE_OPTIONS, config, onConfigChange)
            }
        }
        item("hot") {
            SettingsCategory(title = uiText("热执行点", "Hot execution points"), summary = uiText("高频执行路径，建议短时、配合上限。", "High-frequency paths. Use briefly with limits."), stateKey = "strategy.hot", expandedSections = expandedSections) {
                WarningCard(
                    message = uiText("这些点可能非常频繁。先设置类名前缀、最大记录数和秒数，再逐个打开。", "These points can be very hot. Set class prefix, max records, and max seconds before enabling them one by one."),
                    color = Color(0xFFFFF7DD),
                    icon = { Icon(Icons.Filled.Warning, null, tint = R0Warning, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.padding(12.dp),
                )
                StrategyOptionRows(HOT_OPTIONS, config, onConfigChange)
            }
        }
        item("route") {
            SettingsCategory(title = uiText("路由审计", "Route audit"), summary = uiText("记录 ClassLoader / DexFile / Oat 注册路线。", "Record ClassLoader, DexFile, and Oat registration routes."), stateKey = "strategy.route", expandedSections = expandedSections) {
                StrategyOptionRows(ROUTE_OPTIONS, config, onConfigChange)
            }
        }
        item("force") {
            SettingsCategory(title = uiText("危险主动调用 / force backfill", "Dangerous active call / force backfill"), summary = uiText("主动调用风险最大，默认关闭。", "Active calls are highest risk and disabled by default."), stateKey = "strategy.force", expandedSections = expandedSections) {
                WarningCard(
                    message = uiText("主动调用：force backfill 会主动调用目标方法。必须配合前缀和 max_methods，且优先只允许 static。", "Active call: force backfill actively invokes target methods. Use prefixes and max_methods, and prefer static-only."),
                    modifier = Modifier.padding(12.dp),
                )
                StrategySwitch(uiText("Force backfill（主动调用）", "Force backfill (active call)"), uiText("打开 bit 11/12/13，允许主动调用前/后置回填路径。", "Enable bits 11/12/13 for active before/after backfill routes."), STRATEGY_FORCE_BACKFILL_GROUP, config, onConfigChange)
                SwitchItem(null, uiText("允许 class-walk 触发", "Allow class-walk trigger"), uiText("后端总开关，未设置前缀时保存会自动关掉。", "Backend master switch. Saving disables it automatically when no prefix is set."), config.forceBackfillEnabled) { onConfigChange(config.mutate { forceBackfillEnabled = it }) }
                NumberFieldItem("Force max methods", config.forceBackfillMaxMethods) { onConfigChange(config.mutate { forceBackfillMaxMethods = it }) }
                TextFieldItem(
                    "Force class prefix",
                    config.forceBackfillClassPrefix,
                    uiText(
                        "主动调用前缀；不会再自动覆盖上方类名前缀。",
                        "Active-call prefix; it no longer overwrites the class prefix.",
                    ),
                ) { onConfigChange(config.mutate { forceBackfillClassPrefix = it }) }
            }
        }
    }
}

private fun Modifier.r0PageSwipeBridge(
    enabled: Boolean,
    edgeWidthPx: Float,
    canSwipePrevious: () -> Boolean,
    canSwipeNext: () -> Boolean,
    onPageSwipe: (Int) -> Unit,
): Modifier {
    if (!enabled) {
        return this
    }
    return pointerInput(edgeWidthPx, canSwipePrevious, canSwipeNext, onPageSwipe) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
            val effectiveEdgeWidth = edgeWidthPx
                .coerceAtMost(size.width * 0.18f)
                .coerceAtLeast(viewConfiguration.touchSlop * 2f)
            val allowPrevious = down.position.x <= effectiveEdgeWidth && canSwipePrevious()
            val allowNext = down.position.x >= size.width - effectiveEdgeWidth && canSwipeNext()
            var lastPosition = down.position
            var accumulated = Offset.Zero
            var horizontalGesture = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (horizontalGesture) {
                        val triggerDistance = max(viewConfiguration.touchSlop * 8f, size.width * 0.45f)
                        val pageDelta = when {
                            accumulated.x > 0f && allowPrevious -> -1
                            accumulated.x < 0f && allowNext -> 1
                            else -> 0
                        }
                        if (abs(accumulated.x) > triggerDistance &&
                            abs(accumulated.x) > abs(accumulated.y) * 2.2f &&
                            pageDelta != 0) {
                            onPageSwipe(pageDelta)
                        }
                    }
                    break
                }

                val delta = change.position - lastPosition
                lastPosition = change.position
                accumulated += delta
                if (!horizontalGesture) {
                    val absX = abs(accumulated.x)
                    val absY = abs(accumulated.y)
                    if (absX > viewConfiguration.touchSlop && absX > absY * 1.25f) {
                        horizontalGesture = true
                    }
                }
                if (horizontalGesture) {
                    // The raw log body is itself horizontally scrollable. Once the gesture is
                    // clearly horizontal, consume it after the inner horizontalScroll has had a
                    // chance to move, so the outer pager does not interpret normal log reading as
                    // page navigation. Page changes from the log body are reserved for deliberate
                    // edge swipes at the scroll boundary and are triggered manually on release.
                    change.consume()
                }
            }
        }
    }
}

@Composable
private fun LogcatPage(
    snapshot: MainActivity.UiSnapshot,
    active: Boolean,
    onAutoRefreshLogcat: () -> Unit,
    onLogcatFilterChange: (Boolean) -> Unit,
    onLogPageSwipe: (Int) -> Unit,
) {
    val horizontalLogScroll = rememberScrollState()
    val density = LocalDensity.current
    val logPageEdgeWidthPx = with(density) { 36.dp.toPx() }
    LaunchedEffect(active) {
        if (active) {
            onAutoRefreshLogcat()
            while (true) {
                delay(2000)
                onAutoRefreshLogcat()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("日志", "Logs"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SplicedColumnGroup(title = uiText("原始 logcat", "Raw logcat")) {
            item("r0dump-filter") {
                SwitchItem(
                    icon = null,
                    title = uiText("R0DUMP 过滤", "R0DUMP filter"),
                    summary = uiText(
                        "只显示包含 r0dump 的日志；自动刷新会合并去重并保留最近 2000 行。",
                        "Only show lines containing r0dump; auto refresh merges and keeps the latest 2000 unique lines.",
                    ),
                    checked = snapshot.logcatR0dumpOnly,
                    onCheckedChange = onLogcatFilterChange,
                )
            }
            item("source") {
                val sourceName = if (snapshot.logcatR0dumpOnly) "r0dump" else "logcat"
                MetricRow(
                    title = uiText("来源", "Source"),
                    value = if (snapshot.logcatRunning) uiText("刷新中", "Refreshing") else sourceName,
                    summary = if (snapshot.logcatR0dumpOnly) {
                        "logcat -d -v threadtime -t 300 | filter:r0dump"
                    } else {
                        "logcat -d -v threadtime -t 300"
                    },
                )
            }
            item("raw-logcat") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .r0PageSwipeBridge(
                            enabled = active,
                            edgeWidthPx = logPageEdgeWidthPx,
                            canSwipePrevious = { horizontalLogScroll.value <= 2 },
                            canSwipeNext = {
                                horizontalLogScroll.maxValue <= 2 ||
                                        horizontalLogScroll.value >= horizontalLogScroll.maxValue - 2
                            },
                            onPageSwipe = onLogPageSwipe,
                        )
                        .horizontalScroll(horizontalLogScroll)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(14.dp),
                ) {
                    Text(
                        text = snapshot.logcatText.ifBlank { uiText("暂无日志，点击刷新日志。", "No logs yet. Tap refresh logs.") },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPage(
    snapshot: MainActivity.UiSnapshot,
    onAutoRefreshStatus: () -> Unit,
    onDumpComplete: () -> Unit,
) {
    var watchActive by rememberSaveable(snapshot.selectedPackage) { mutableStateOf(false) }
    LaunchedEffect(snapshot.dumpEnabled, snapshot.statusDumping, snapshot.statusComplete) {
        if (!snapshot.statusComplete && (snapshot.dumpEnabled || snapshot.statusDumping)) {
            watchActive = true
        }
    }
    LaunchedEffect(snapshot.dumpEnabled, snapshot.statusPath, snapshot.statusDumping, snapshot.statusComplete, watchActive) {
        val shouldPoll = !snapshot.statusComplete && (snapshot.dumpEnabled || snapshot.statusDumping || watchActive)
        while (shouldPoll) {
            delay(1000)
            onAutoRefreshStatus()
        }
    }
    LaunchedEffect(snapshot.statusPath, snapshot.statusComplete, watchActive) {
        if (watchActive && snapshot.statusPath.isNotBlank() && snapshot.statusComplete) {
            watchActive = false
            onDumpComplete()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("状态", "Status"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (!snapshot.dumpEnabled && snapshot.statusPath.isBlank()) {
            StatusSkeletonCard()
        } else {
            StatusDashboard(snapshot)
        }
    }
}

@Composable
private fun StatusDashboard(snapshot: MainActivity.UiSnapshot) {
    val status = snapshot.status
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!status.available) {
            SplicedColumnGroup(title = uiText("运行状态", "Run state")) {
                item("pending-status") {
                    StatusStateRow(
                        title = if (status.readError) uiText("状态读取失败", "Status read failed") else uiText("等待状态文件", "Waiting for status file"),
                        value = if (snapshot.statusRefreshRunning) uiText("刷新中", "Refreshing") else snapshot.phase,
                        summary = status.message.ifBlank { snapshot.phaseDescription },
                        tone = if (status.readError) R0Danger else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            SplicedColumnGroup(title = uiText("预计路径", "Expected paths")) {
                item("scan") { PathMetricRow(uiText("扫描目录", "Scan directory"), snapshot.scanDir, "DEX ${snapshot.dexCount} · Records ${snapshot.recordCount}") }
                item("output") { PathMetricRow(uiText("输出目录", "Output dir"), snapshot.outputDir, uiText("公开导出", "Public export")) }
            }
            return@Column
        }

        SplicedColumnGroup(title = uiText("运行状态", "Run state")) {
            item("phase") {
                StatusStateRow(
                    title = uiText("阶段", "Phase"),
                    value = status.phaseLabel.ifBlank { snapshot.phase },
                    summary = status.phaseRaw.ifBlank { uiText("无原始阶段", "No raw phase") },
                    tone = when {
                        snapshot.statusComplete -> R0Success
                        snapshot.statusDumping || snapshot.dumpEnabled -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                )
            }
            item("target") {
                DetailMetricRow(
                    title = uiText("目标", "Target"),
                    value = status.packageName.ifBlank { snapshot.selectedPackage.ifBlank { uiText("无", "None") } },
                    summary = uiText("进程", "Process") + " ${status.processName.ifBlank { uiText("未知", "Unknown") }} · pid=${status.pid}",
                )
            }
            item("updated") {
                MetricRow(
                    title = uiText("最后更新", "Last update"),
                    value = status.updatedAt.ifBlank { uiText("无", "None") },
                    summary = if (status.startedAt.isNotBlank()) uiText("开始", "Started") + " ${status.startedAt}" else "",
                )
            }
        }

        SplicedColumnGroup(title = uiText("输出计数", "Output counts")) {
            item("counts") {
                StatusMetricGrid(
                    listOf(
                        uiText("DEX 文件", "Dex files") to status.dexFilesWritten.toString(),
                        uiText("Raw dexdata", "Raw dexdata") to status.dexDataFilesWritten.toString(),
                        uiText("方法记录", "Method records") to status.methodRecordsWritten.toString(),
                        uiText("重复跳过", "Duplicates skipped") to status.duplicateMethodsSkipped.toString(),
                        uiText("非标准 DEX 跳过", "Non-standard DEX skipped")
                                to status.nonstandardDexMethodsSkipped.toString(),
                        uiText("非法方法", "Invalid methods") to status.invalidMethodsSkipped.toString(),
                    ),
                )
            }
        }

        SplicedColumnGroup(title = uiText("Force backfill", "Force backfill")) {
            item("backfill") {
                StatusMetricGrid(
                    listOf(
                        uiText("已调用", "Invoked") to status.forceBackfillAttempts.toString(),
                        uiText("变化成功", "Changed") to status.forceBackfillChangedSuccess.toString(),
                        uiText("调用未变化", "Unchanged") to status.forceBackfillInvokedUnchanged.toString(),
                        uiText("Guard 跳过", "Guard skipped") to status.forceBackfillSkippedByGuard.toString(),
                        uiText("执行异常", "Invoke exceptions") to status.forceBackfillInvokeExceptions.toString(),
                        uiText("失败", "Failed") to status.forceBackfillFailed.toString(),
                    ),
                )
            }
        }

        SplicedColumnGroup(title = uiText("ClassLoader 覆盖", "ClassLoader coverage")) {
            item("classloader-counts") {
                StatusMetricGrid(
                    listOf(
                        uiText("候选", "Candidates") to status.classLoaderCandidates.toString(),
                        uiText("已扫描", "Walked") to status.classLoadersWalked.toString(),
                        uiText("Dex elements", "Dex elements") to status.classLoaderDexElements.toString(),
                        uiText("Dex cookies", "Dex cookies") to status.classLoaderUniqueCookies.toString(),
                        uiText("已加载类", "Loaded classes") to status.loadedClassTableClasses.toString(),
                        uiText("Manifest 组件", "Manifest components") to status.manifestComponentClasses.toString(),
                        uiText("Manifest dump", "Manifest dumped") to status.manifestSeedDumped.toString(),
                    ),
                )
            }
            if (status.classLoaders.isNotBlank()) {
                item("classloader-list") {
                    LongDetailMetricRow(
                        title = uiText("ClassLoader 列表", "ClassLoader list"),
                        value = status.classLoaders,
                        summary = uiText("按本次扫描顺序记录", "Recorded in scan order"),
                    )
                }
            }
        }

        SplicedColumnGroup(title = uiText("路径与策略", "Paths and strategies")) {
            item("output") { PathMetricRow(uiText("工作输出", "Working output"), status.outputDir.ifBlank { snapshot.outputDir }, uiText("状态记录中的输出目录", "Output dir from status")) }
            item("status") { PathMetricRow(uiText("状态文件", "Status file"), status.filePath, uiText("当前读取", "Currently read")) }
            item("strategies") { DetailMetricRow(uiText("启用策略", "Active strategies"), status.strategies, "strategy_mask") }
        }
    }
}

@Composable
private fun StatusStateRow(title: String, value: String, summary: String, tone: Color) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Text(
                summary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { StatusPill(value, tone) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusMetricGrid(metrics: List<Pair<String, String>>) {
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        metrics.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    StatusMetricTile(label, value, Modifier.weight(1f))
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatusMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RepairPage(snapshot: MainActivity.UiSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText("修复", "Repair"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        SplicedColumnGroup(title = uiText("产物摘要", "Output summary")) {
            item("scan") {
                PathMetricRow(
                    title = uiText("扫描目录", "Scan directory"),
                    path = snapshot.scanDir,
                    summary = "DEX/raw ${snapshot.dexCount} · Records ${snapshot.recordCount}",
                )
            }
            item("status") {
                PathMetricRow(
                    title = uiText("状态文件", "Status file"),
                    path = snapshot.statusPath.ifBlank { uiText("未发现", "Not found") },
                    summary = uiText("输出目录", "Output dir") + " ${snapshot.outputDir}",
                )
            }
            item("zip") { PathMetricRow(uiText("修复 ZIP", "Repair ZIP"), snapshot.zipPath, uiText("输出目录", "Output dir") + " ${snapshot.outputDir}") }
        }
        FileGroup(uiText("DEX / Raw dexdata", "DEX / Raw dexdata"), snapshot.dexFiles)
        FileGroup("Method Records", snapshot.recordFiles)
        if (snapshot.repairedFiles.isNotEmpty()) {
            FileGroup(uiText("修复产物", "Repaired outputs"), snapshot.repairedFiles)
        }
        if (snapshot.repairRunning || snapshot.repairProgress.startsWith("修复失败") || snapshot.repairProgress.contains("100%")) {
            SplicedColumnGroup(title = uiText("修复进度", "Repair progress")) {
                item("progress") { DetailMetricRow(uiText("状态", "Status"), snapshot.repairProgress, if (snapshot.repairRunning) uiText("正在修复", "Repairing") else uiText("完成", "Complete")) }
            }
        }
    }
}

@Composable
private fun FileGroup(title: String, files: List<MainActivity.UiFileEntry>) {
    SplicedColumnGroup(title = title) {
        if (files.isEmpty()) {
            item("empty-$title") {
                ListItem(
                    headlineContent = { Text(uiText("暂无文件", "No files")) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        } else {
            files.take(30).forEach { file ->
                item(file.path) {
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Monospace) },
                        supportingContent = { Text("${file.size} · ${file.updated}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun StrategyOptionRows(options: List<StrategyOption>, config: MainActivity.UiConfig, onConfigChange: (MainActivity.UiConfig) -> Unit) {
    options.forEach { option ->
        StrategySwitch(option.title, option.summary, option.bit, config, onConfigChange)
    }
}

@Composable
private fun StrategySwitch(
    title: String,
    summary: String,
    bit: Int,
    config: MainActivity.UiConfig,
    onConfigChange: (MainActivity.UiConfig) -> Unit,
) {
    val checked = if (bit == STRATEGY_FORCE_BACKFILL_GROUP) {
        (config.strategyMask and STRATEGY_FORCE_BACKFILL_GROUP) != 0
    } else {
        (config.strategyMask and bit) != 0
    }
    SwitchItem(
        icon = null,
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = { enabled ->
            onConfigChange(config.mutate {
                strategyMask = if (bit == STRATEGY_FORCE_BACKFILL_GROUP) {
                    if (enabled) strategyMask or STRATEGY_FORCE_BACKFILL_GROUP else strategyMask and STRATEGY_FORCE_BACKFILL_GROUP.inv()
                } else {
                    if (enabled) strategyMask or bit else strategyMask and bit.inv()
                }
            })
        },
    )
}

private const val FloatingLabelSentinel = "​"

@Composable
private fun TextFieldItem(
    title: String,
    value: String,
    hint: String = "",
    alwaysFloatLabel: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    val displayValue = if (alwaysFloatLabel && value.isEmpty()) FloatingLabelSentinel else value
    ListItem(
        headlineContent = {
            OutlinedTextField(
                value = displayValue,
                onValueChange = { raw ->
                    onValueChange(if (alwaysFloatLabel) raw.replace(FloatingLabelSentinel, "") else raw)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(title) },
                placeholder = if (hint.isBlank()) null else ({ Text(hint) }),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun NumberFieldItem(title: String, value: String, hint: String = "", onValueChange: (String) -> Unit) {
    val effectiveHint = hint.ifBlank { uiText("0 为不限", "0 = unlimited") }
    ListItem(
        headlineContent = {
            OutlinedTextField(
                value = value,
                onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() }) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(title) },
                placeholder = { Text(effectiveHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun OptionSelectItem(
    title: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val selected = options.firstOrNull { it.first == value }
    ListItem(
        headlineContent = {
            Box(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selected?.first ?: value,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    singleLine = true,
                    label = { Text(title) },
                    supportingText = {
                        Text(selected?.second ?: uiText("选择一个有效模式", "Select a valid mode"))
                    },
                    trailingIcon = {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            null,
                            modifier = Modifier.rotate(if (expanded) 180f else 0f),
                        )
                    },
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(role = Role.Button) { expanded = true },
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.fillMaxWidth(0.92f),
                ) {
                    options.forEach { (option, summary) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(option, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onValueChange(option)
                            },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun StatusSkeletonCard() {
    ExpressiveCard(flat = true) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SkeletonLine(Modifier.fillMaxWidth(0.42f).height(18.dp))
            SkeletonLine(Modifier.fillMaxWidth(0.86f).height(14.dp))
            SkeletonLine(Modifier.fillMaxWidth(0.68f).height(14.dp))
            Spacer(Modifier.height(4.dp))
            SkeletonLine(Modifier.fillMaxWidth(0.52f).height(14.dp))
        }
    }
}

@Composable
private fun SkeletonLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)),
    )
}

@Composable
private fun PathMetricRow(title: String, path: String, summary: String) {
    ListItem(
        headlineContent = {
            Text(
                title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    path.ifBlank { "未生成" },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun DetailMetricRow(title: String, value: String, summary: String) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    value,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun LongDetailMetricRow(title: String, value: String, summary: String) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    value,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun MetricRow(title: String, value: String, summary: String = "") {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = if (summary.isBlank()) null else { { Text(summary, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Text(
                value,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private data class ActionItem(
    val label: String,
    val action: () -> Unit,
    val enabled: Boolean = true,
)

@Composable
private fun ActionGrid(
    primary: List<ActionItem> = emptyList(),
    secondary: List<ActionItem> = emptyList(),
    danger: List<ActionItem> = emptyList(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (primary.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            primary.forEach { item ->
                Button(onClick = item.action, enabled = item.enabled, modifier = Modifier.weight(1f)) {
                    Icon(primaryActionIcon(item.label), null)
                    Spacer(Modifier.width(8.dp))
                    Text(item.label)
                }
            }
        }
        if (secondary.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            secondary.forEach { item ->
                OutlinedButton(onClick = item.action, enabled = item.enabled, modifier = Modifier.weight(1f)) {
                    Text(item.label)
                }
            }
        }
        if (danger.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            danger.forEach { item ->
                OutlinedButton(
                    onClick = item.action,
                    enabled = item.enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = R0Danger),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text(item.label)
                }
            }
        }
    }
}

private fun primaryActionIcon(label: String): ImageVector = when {
    label.contains("保存") -> Icons.Filled.Save
    label.contains("查看") -> Icons.Filled.Folder
    else -> Icons.Filled.PlayArrow
}

@Composable
private fun AppAvatar(label: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull()?.uppercaseChar()?.toString() ?: "R",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PhaseChip(text: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (enabled) R0Success else R0Muted),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val LocalInsideSplicedGroup = compositionLocalOf { false }
private val CornerRadius = 16.dp
private val ConnectionRadius = 5.dp

private data class SplicedItemData(
    val key: Any?,
    val visible: Boolean,
    val content: @Composable () -> Unit,
)

private class SplicedGroupScope {
    val items = mutableListOf<SplicedItemData>()
    fun item(key: Any? = null, visible: Boolean = true, content: @Composable () -> Unit) {
        items.add(SplicedItemData(key ?: items.size, visible, content))
    }
}

@Composable
private fun SplicedColumnGroup(
    modifier: Modifier = Modifier,
    title: String = "",
    flat: Boolean = false,
    content: SplicedGroupScope.() -> Unit,
) {
    val scope = SplicedGroupScope().apply(content)
    val allItems = scope.items
    if (allItems.isEmpty()) return

    CompositionLocalProvider(LocalInsideSplicedGroup provides true) {
        Column(modifier = modifier.padding(horizontal = 0.dp, vertical = 2.dp)) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
            }
            Column(verticalArrangement = Arrangement.Top) {
                val firstVisibleIndex = allItems.indexOfFirst { it.visible }
                val lastVisibleIndex = allItems.indexOfLast { it.visible }
                val sharedStiffness = Spring.StiffnessMediumLow
                val canAnimateShape = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                allItems.forEachIndexed { index, itemData ->
                    key(itemData.key) {
                        AnimatedVisibility(
                            visible = itemData.visible,
                            modifier = Modifier.zIndex(if (itemData.visible) 0f else 1f),
                            enter = expandVertically(animationSpec = spring(stiffness = sharedStiffness), expandFrom = Alignment.Top) + fadeIn(animationSpec = spring(stiffness = sharedStiffness)),
                            exit = shrinkVertically(animationSpec = spring(stiffness = sharedStiffness), shrinkTowards = Alignment.Top) + fadeOut(animationSpec = spring(stiffness = sharedStiffness)),
                        ) {
                            val isFirst = index == firstVisibleIndex
                            val isLast = index == lastVisibleIndex
                            val topRadius = if (isFirst) CornerRadius else ConnectionRadius
                            val bottomRadius = if (isLast) CornerRadius else ConnectionRadius
                            val currentTopRadius = if (canAnimateShape) animateDpAsState(topRadius, spring(stiffness = sharedStiffness), label = "TopRadius").value else topRadius
                            val currentBottomRadius = if (canAnimateShape) animateDpAsState(bottomRadius, spring(stiffness = sharedStiffness), label = "BottomRadius").value else bottomRadius
                            val currentTopPadding = if (canAnimateShape) animateDpAsState(if (isFirst) 0.dp else 2.dp, spring(stiffness = sharedStiffness), label = "TopPadding").value else if (isFirst) 0.dp else 2.dp
                            val shape = RoundedCornerShape(currentTopRadius, currentTopRadius, currentBottomRadius, currentBottomRadius)
                            Column(
                                modifier = Modifier
                                    .padding(top = currentTopPadding)
                                    .clip(shape)
                                    .background(if (flat) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface, shape),
                            ) { itemData.content() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpressiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    flat: Boolean = false,
    content: @Composable () -> Unit,
) {
    if (LocalInsideSplicedGroup.current) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(),
                        onClick = onClick,
                    ) else Modifier,
                ),
        ) { content() }
        return
    }
    val shape = RoundedCornerShape(32.dp)
    val colors = if (flat) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    }
    if (flat) {
        if (onClick != null) Card(modifier = modifier.fillMaxWidth(), colors = colors, onClick = onClick, shape = shape, content = { content() })
        else Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, content = { content() })
    } else {
        if (onClick != null) Card(modifier = modifier.fillMaxWidth(), colors = colors, onClick = onClick, shape = shape, content = { content() })
        else Card(modifier = modifier.fillMaxWidth(), colors = colors, shape = shape, content = { content() })
    }
}

@Composable
private fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        thumbContent = {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector?,
    title: String,
    summary: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        trailingContent = { ExpressiveSwitch(checked = checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch, enabled = enabled),
    )
}

@Composable
private fun SettingsCategory(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    initialExpanded: Boolean = false,
    isSearching: Boolean = false,
    stateKey: String = title,
    expandedSections: MutableMap<String, Boolean>? = null,
    content: @Composable () -> Unit,
) {
    var localExpanded by rememberSaveable(stateKey) { mutableStateOf(initialExpanded) }
    val expanded = expandedSections?.get(stateKey) ?: localExpanded
    fun setExpanded(value: Boolean) {
        if (expandedSections != null) {
            expandedSections[stateKey] = value
        } else {
            localExpanded = value
        }
    }
    val rotationState by animateFloatAsState(targetValue = if (expanded || isSearching) 180f else 0f, label = "ArrowRotation")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(R0PageTransitionMillis, easing = R0EaseOutQuint)),
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) },
            supportingContent = summary?.let { { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline) } },
            leadingContent = icon?.let { { Icon(it, null) } },
            trailingContent = {
                if (!isSearching) Icon(Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.rotate(rotationState))
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable(enabled = !isSearching) { setExpanded(!expanded) },
        )
        AnimatedVisibility(visible = expanded || isSearching) { Column { content() } }
    }
}

@Composable
private fun WarningCard(
    message: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = color ?: MaterialTheme.colorScheme.errorContainer,
        contentColor = if (color == null) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = color ?: MaterialTheme.colorScheme.errorContainer,
        disabledContentColor = MaterialTheme.colorScheme.onErrorContainer,
    )
    Card(colors = cardColors, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(onClick?.let { Modifier.clickable { it() } } ?: Modifier)
                .padding(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
                    .padding(end = if (onClose != null) 40.dp else 0.dp),
            ) {
                if (icon != null) icon() else Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.wrapContentHeight(Alignment.CenterVertically))
            }
            if (onClose != null) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.clickable { onClose() }.size(18.dp).align(Alignment.TopEnd))
            }
        }
    }
}

private data class StrategyOption(val title: String, val summary: String, val bit: Int)

private val STRATEGY_FORCE_BACKFILL_GROUP = MainActivity.STRATEGY_FORCE_BACKFILL or
    MainActivity.STRATEGY_FORCE_BACKFILL_BEFORE or
    MainActivity.STRATEGY_FORCE_BACKFILL_AFTER

private val TRIGGER_OPTIONS = listOf(
    StrategyOption("Application create", "Application attach/create 前启用 runtime。", MainActivity.STRATEGY_APP_CREATE),
)
private val DEX_OPEN_OPTIONS = listOf(
    StrategyOption("InMemoryDex", "ByteBuffer/InMemoryDexClassLoader 壳优先打开。", MainActivity.STRATEGY_IN_MEMORY_DEX),
    StrategyOption("RegisterDex", "ART 注册 dex 时采集，重复概率较高。", MainActivity.STRATEGY_REGISTER_DEX),
    StrategyOption("Dex load", "Java/Native DexFile 加载路径。", MainActivity.STRATEGY_DEX_LOAD),
    StrategyOption("Open common", "DexFileLoader 中心打开点，覆盖面广。", MainActivity.STRATEGY_OPEN_COMMON),
    StrategyOption("OpenDexFilesFromOat", "从 oat/vdex 打开 dex 时导出。", MainActivity.STRATEGY_OPEN_DEX_FILES_FROM_OAT),
    StrategyOption("Vdex OpenAllDexFiles", "Vdex OpenAllDexFiles 路线导出。", MainActivity.STRATEGY_VDEX_OPEN_ALL_DEX_FILES),
    StrategyOption("OatDexFile OpenDexFile", "OatDexFile OpenDexFile 路线导出。", MainActivity.STRATEGY_OAT_DEX_FILE_OPEN),
    StrategyOption("defineClassNative", "DexFile.defineClassNative 入口 dump dex。", MainActivity.STRATEGY_DEFINE_CLASS_NATIVE),
    StrategyOption("ImageSpace dex", "App image / AddImageSpaces 里的 dex 导出。", MainActivity.STRATEGY_IMAGE_SPACE_DEX),
)
private val CLASS_LIFECYCLE_OPTIONS = listOf(
    StrategyOption("LoadMethod", "ClassLinker LoadMethod 时 dump，触发早但开销更大。", MainActivity.STRATEGY_LOAD_METHOD),
    StrategyOption("DefineClass", "ClassLinker::DefineClass 导出 raw/fixed dex；方法记录由延迟 class-walk 生成，属于性能敏感高级策略。", MainActivity.STRATEGY_DEFINE_CLASS),
    StrategyOption("LoadClass", "LoadClass 后 dump 该类方法。", MainActivity.STRATEGY_LOAD_CLASS),
    StrategyOption("ResolveMethod", "高频点，仅单独短时测试。", MainActivity.STRATEGY_RESOLVE_METHOD),
    StrategyOption("VerifyClass", "类校验完成后 dump。", MainActivity.STRATEGY_VERIFY_CLASS),
    StrategyOption("Class init before", "<clinit> 执行前 dump。", MainActivity.STRATEGY_CLASS_INIT_BEFORE),
    StrategyOption("Class init after", "<clinit> 成功后 dump。", MainActivity.STRATEGY_CLASS_INIT_AFTER),
)
private val HOT_OPTIONS = listOf(
    StrategyOption("Real invoke（被动调用）", "被动调用：目标方法自然执行到真实调用路径时采集，不主动触发目标方法。", MainActivity.STRATEGY_REAL_INVOKE),
    StrategyOption("Interpreter Execute", "Interpreter Execute 热路径，建议只短时打开。", MainActivity.STRATEGY_INTERPRETER_EXECUTE),
    StrategyOption("JIT MethodEntered", "JIT MethodEntered 采样路线。", MainActivity.STRATEGY_JIT_METHOD_ENTERED),
    StrategyOption("JIT Compile", "JIT 编译前 dump，适合追补热点方法。", MainActivity.STRATEGY_JIT_COMPILE),
    StrategyOption("Reflect Method.invoke", "反射 Method.invoke 前采集。", MainActivity.STRATEGY_REFLECT_METHOD_INVOKE),
    StrategyOption("Instrumentation enter", "Instrumentation enter 回调采集。", MainActivity.STRATEGY_INSTRUMENT_METHOD_ENTER),
    StrategyOption("Instrumentation exit", "Instrumentation exit 回调采集。", MainActivity.STRATEGY_INSTRUMENT_METHOD_EXIT),
)
private val ROUTE_OPTIONS = listOf(
    StrategyOption("Java ClassLoader route", "记录 ClassLoader 路线，帮助复原加载时间线。", MainActivity.STRATEGY_JAVA_CLASS_LOADER_ROUTE),
    StrategyOption("Java DexFile route", "记录 Java DexFile 构造/open/loadClass 路线。", MainActivity.STRATEGY_JAVA_DEXFILE_ROUTE),
    StrategyOption("Oat register", "OatFileManager RegisterOatFile 审计。", MainActivity.STRATEGY_OAT_REGISTER),
)

private inline fun MainActivity.UiConfig.mutate(block: MainActivity.UiConfig.() -> Unit): MainActivity.UiConfig = copy().apply(block)
