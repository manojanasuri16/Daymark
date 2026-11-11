package com.daymark.app.ui

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.daymark.app.data.Settings
import com.daymark.app.ui.calendar.CalendarScreen
import com.daymark.app.ui.components.LocalHapticsEnabled
import com.daymark.app.ui.dashboard.DashboardScreen
import com.daymark.app.ui.insights.InsightsScreen
import com.daymark.app.ui.lock.LockScreen
import com.daymark.app.ui.log.LogScreen
import com.daymark.app.ui.onboarding.OnboardingScreen
import com.daymark.app.ui.settings.SettingsScreen
import com.daymark.app.ui.widgets.WidgetGalleryScreen
import com.daymark.app.ui.theme.Accent
import com.daymark.app.ui.theme.DaymarkTheme
import com.daymark.app.ui.habits.HabitEditorScreen
import com.daymark.app.ui.theme.Motion
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    /** Locked until proven otherwise when app lock is on. */
    private var locked by mutableStateOf(true)
    private var backgroundedAt = 0L

    /** Set by a shortcut, Quick Settings tile or notification; consumed once the app is unlocked. */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) locked = false
        if (savedInstanceState == null) pendingRoute = intent?.getStringExtra(com.daymark.app.notify.Routes.EXTRA)

        // Re-lock after the app has been in the background for a short grace period
        // (the grace keeps file pickers for backup/restore from forcing a re-unlock).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { backgroundedAt = SystemClock.elapsedRealtime() }
            override fun onStart(owner: LifecycleOwner) {
                if (backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > LOCK_GRACE_MS) locked = true
            }
        })

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val habits by vm.habits.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            LaunchedEffect(s.hideInRecents) {
                if (s.hideInRecents) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            DaymarkTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor, accent = Accent.of(s.accent)) {
                CompositionLocalProvider(LocalHapticsEnabled provides s.haptics) {
                    // Surface (not Box) so every Text inherits onBackground — otherwise Compose defaults to black,
                    // which is unreadable on the dark theme (onboarding & lock screens have no Scaffold).
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                        val showLock = s.appLock && locked
                        AnimatedContent(
                            targetState = when {
                                showLock -> Gate.LOCKED
                                !s.onboarded -> Gate.ONBOARDING
                                habits.isNullOrEmpty() -> Gate.LOADING // v1 → v2 migration creates tracker #1 moments after start
                                else -> Gate.APP
                            },
                            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.standard()) },
                            label = "gate",
                        ) { gate ->
                            when (gate) {
                                Gate.LOCKED -> LockScreen(onUnlocked = { locked = false })
                                Gate.ONBOARDING -> OnboardingScreen { habit -> vm.completeOnboarding(habit) }
                                Gate.LOADING -> Box(Modifier.fillMaxSize())
                                Gate.APP -> DaymarkNav(vm, s, pendingRoute) { pendingRoute = null }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(com.daymark.app.notify.Routes.EXTRA)?.let { pendingRoute = it }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, !locked)
    }

    private enum class Gate { LOCKED, ONBOARDING, LOADING, APP }

    companion object {
        private const val KEY_UNLOCKED = "unlocked"
        private const val LOCK_GRACE_MS = 20_000L
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.WbSunny),
    CALENDAR("calendar", "Calendar", Icons.Rounded.CalendarMonth),
    INSIGHTS("insights", "Insights", Icons.Rounded.Insights),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}

private const val HABIT_ROUTE = "habit?id={id}"
private fun habitRoute(id: Long = 0) = "habit?id=$id"

private const val LOG_ROUTE = "log?id={id}&day={day}&note={note}"
private fun logRoute(id: Long = 0, day: Long? = null, note: Long = 0) = "log?id=$id&day=${day ?: Long.MIN_VALUE}&note=$note"

private const val URGE_ROUTE = "urge?id={id}&day={day}"
private fun urgeRoute(id: Long = 0, day: Long? = null) = "urge?id=$id&day=${day ?: Long.MIN_VALUE}"

private const val JOURNAL_ROUTE = "journal?id={id}&day={day}"
private fun journalRoute(id: Long = 0, day: Long? = null) = "journal?id=$id&day=${day ?: Long.MIN_VALUE}"

private val entryArgs = listOf(
    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
    navArgument("day") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
)

@Composable
private fun DaymarkNav(vm: MainViewModel, settings: Settings, pendingRoute: String?, onRouteHandled: () -> Unit) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTab = Tab.entries.any { it.route == route }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    LaunchedEffect(pendingRoute) {
        val target = when (pendingRoute) {
            com.daymark.app.notify.Routes.URGE -> urgeRoute()
            com.daymark.app.notify.Routes.JOURNAL -> journalRoute()
            com.daymark.app.notify.Routes.SLIP -> logRoute()
            else -> null
        }
        if (pendingRoute != null) {
            if (target != null) nav.navigate(target) { launchSingleTop = true }
            onRouteHandled()
        }
    }

    fun selectTab(tab: Tab) = nav.navigate(tab.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(
                visible = isTab,
                enter = slideInVertically(Motion.standard()) { it } + fadeIn(),
                exit = slideOutVertically(Motion.standard()) { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { selectTab(tab) },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.TODAY.route,
            // Tabs cross-fade with a hint of scale (no lateral motion between peers).
            enterTransition = { fadeIn(tween(220, delayMillis = 60)) + scaleIn(Motion.standard(), initialScale = 0.98f) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(220, delayMillis = 60)) + scaleIn(Motion.standard(), initialScale = 0.98f) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable(Tab.TODAY.route) {
                DashboardScreen(
                    vm, padding,
                    onLogSlip = { nav.navigate(logRoute()) },
                    onUrge = { nav.navigate(urgeRoute()) },
                    onJournal = { nav.navigate(journalRoute()) },
                    onOpenCalendar = { selectTab(Tab.CALENDAR) },
                    onAddHabit = { nav.navigate(habitRoute()) },
                    onEditHabit = { nav.navigate(habitRoute(it.id)) },
                )
            }
            composable(Tab.CALENDAR.route) {
                CalendarScreen(
                    vm, padding,
                    onLogForDay = { nav.navigate(logRoute(day = it.toEpochDay())) },
                    onEdit = { nav.navigate(logRoute(id = it.id)) },
                    onUrgeForDay = { nav.navigate(urgeRoute(day = it.toEpochDay())) },
                    onEditUrge = { nav.navigate(urgeRoute(id = it.id)) },
                    onJournalForDay = { nav.navigate(journalRoute(day = it.toEpochDay())) },
                    onEditJournal = { nav.navigate(journalRoute(id = it.id)) },
                    onEditNote = { nav.navigate(logRoute(note = it.id)) },
                )
            }
            composable(Tab.INSIGHTS.route) { InsightsScreen(vm, padding) }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    vm, padding,
                    onOpenWidgets = { nav.navigate("widgets") },
                    onAddHabit = { nav.navigate(habitRoute()) },
                    onEditHabit = { nav.navigate(habitRoute(it.id)) },
                )
            }
            composable(
                HABIT_ROUTE,
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
                enterTransition = { slideInVertically(Motion.standard(0.45f)) { it } + fadeIn() },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(Motion.standard(0.4f)) { it } + fadeOut() },
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: 0L
                val habits by vm.habits.collectAsStateWithLifecycle()
                val existing = habits?.firstOrNull { it.id == id }
                if (id != 0L && existing == null) return@composable
                HabitEditorScreen(
                    existing = existing,
                    canDelete = (habits?.size ?: 0) > 1,
                    onSave = { vm.saveHabit(it); nav.popBackStack() },
                    onDelete = { vm.deleteHabit(it); nav.popBackStack() },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                "widgets",
                enterTransition = { slideInHorizontally(Motion.standard()) { it } + fadeIn() },
                popExitTransition = { slideOutHorizontally(Motion.standard()) { it } + fadeOut() },
            ) {
                WidgetGalleryScreen(vm, onBack = { nav.popBackStack() }, onMessage = { m -> scope.launch { snackbar.showSnackbar(m) } })
            }
            composable(
                URGE_ROUTE, arguments = entryArgs,
                enterTransition = { slideInVertically(Motion.standard(0.45f)) { it } + fadeIn() },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(Motion.standard(0.4f)) { it } + fadeOut() },
            ) { entry ->
                com.daymark.app.ui.urge.UrgeScreen(
                    vm = vm,
                    editId = entry.arguments?.getLong("id") ?: 0L,
                    initialDate = entry.arguments?.getLong("day")?.takeIf { it != Long.MIN_VALUE },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                JOURNAL_ROUTE, arguments = entryArgs,
                enterTransition = { slideInVertically(Motion.standard(0.45f)) { it } + fadeIn() },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(Motion.standard(0.4f)) { it } + fadeOut() },
            ) { entry ->
                com.daymark.app.ui.journal.JournalScreen(
                    vm = vm,
                    editId = entry.arguments?.getLong("id") ?: 0L,
                    initialDate = entry.arguments?.getLong("day")?.takeIf { it != Long.MIN_VALUE },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                LOG_ROUTE,
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("day") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                    navArgument("note") { type = NavType.LongType; defaultValue = 0L },
                ),
                // A modal task: rises from the bottom, and leaves the way it came.
                enterTransition = { slideInVertically(Motion.standard(0.45f)) { it } + fadeIn() },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { slideOutVertically(Motion.standard(0.4f)) { it } + fadeOut() },
            ) { entry ->
                val noteId = entry.arguments?.getLong("note") ?: 0L
                val id = entry.arguments?.getLong("id") ?: 0L
                val day = entry.arguments?.getLong("day")?.takeIf { it != Long.MIN_VALUE }
                val logHabit by vm.selectedHabit.collectAsStateWithLifecycle()
                // Build habits log progress, not slips.
                if (logHabit?.isBuild == true && id == 0L && noteId == 0L) {
                    com.daymark.app.ui.log.BuildProgressScreen(vm = vm, onClose = { nav.popBackStack() })
                    return@composable
                }
                LogScreen(
                    vm = vm,
                    editId = id,
                    noteId = noteId,
                    initialDate = day,
                    onClose = { nav.popBackStack() },
                    onDeleted = { record ->
                        nav.popBackStack()
                        scope.launch {
                            val r = snackbar.showSnackbar("Entry deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
                            if (r == SnackbarResult.ActionPerformed) vm.restoreRelapse(record)
                        }
                    },
                )
            }
        }
    }
}
