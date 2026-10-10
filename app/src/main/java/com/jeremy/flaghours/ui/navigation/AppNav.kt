package com.jeremy.flaghours.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.jeremy.flaghours.ui.common.LocalHasBottomBar
import com.jeremy.flaghours.ui.common.app
import com.jeremy.flaghours.ui.dashboard.DashboardScreen
import com.jeremy.flaghours.ui.jobs.JobsScreen
import com.jeremy.flaghours.ui.paycheck.PaycheckScreen
import com.jeremy.flaghours.ui.ro.RoDetailScreen
import com.jeremy.flaghours.ui.ro.RoEditScreen
import com.jeremy.flaghours.ui.scan.ScanScreen
import com.jeremy.flaghours.ui.settings.PlanEditScreen
import com.jeremy.flaghours.ui.settings.SettingsScreen
import com.jeremy.flaghours.ui.setup.SetupWizardScreen
import com.jeremy.flaghours.ui.time.TimeScreen
import kotlin.reflect.KClass

private data class Tab(val route: Any, val cls: KClass<*>, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(DashboardRoute, DashboardRoute::class, "Dashboard", Icons.Default.Home),
    Tab(JobsRoute, JobsRoute::class, "Jobs", Icons.AutoMirrored.Filled.List),
    Tab(TimeRoute, TimeRoute::class, "Time", Icons.Default.AccessTime),
    Tab(PaycheckRoute, PaycheckRoute::class, "Paycheck", Icons.Default.Receipt),
)

@Composable
fun AppNav() {
    val repo = app().repository
    var start by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(Unit) { start = if (repo.currentPlan().setupComplete) DashboardRoute else SetupRoute }
    val startRoute = start ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val dest = entry?.destination
    val onTab = tabs.any { t -> dest?.hasRoute(t.cls) == true }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = dest?.hasRoute(t.cls) == true,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        CompositionLocalProvider(LocalHasBottomBar provides onTab) {
            NavHost(nav, startDestination = startRoute, modifier = Modifier.padding(pad)) {
                composable<SetupRoute> {
                    SetupWizardScreen(onDone = {
                        nav.navigate(DashboardRoute) { popUpTo(0) { inclusive = true } }
                    })
                }
                composable<DashboardRoute> {
                    DashboardScreen(
                        onSettings = { nav.navigate(SettingsRoute) },
                        onNewRo = { nav.navigate(RoEditRoute()) },
                        onScan = { nav.navigate(ScanRoute) },
                        onPaycheck = { nav.navigate(PaycheckRoute) { launchSingleTop = true } },
                    )
                }
                composable<JobsRoute> {
                    JobsScreen(
                        onOpenRo = { nav.navigate(RoDetailRoute(it)) },
                        onNewRo = { nav.navigate(RoEditRoute()) },
                        onScan = { nav.navigate(ScanRoute) },
                        onSettings = { nav.navigate(SettingsRoute) },
                    )
                }
                composable<TimeRoute> { TimeScreen(onSettings = { nav.navigate(SettingsRoute) }) }
                composable<PaycheckRoute> {
                    PaycheckScreen(onSettings = { nav.navigate(SettingsRoute) }, onOpenRo = { nav.navigate(RoDetailRoute(it)) })
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        onBack = { nav.popBackStack() },
                        onEditPlan = { nav.navigate(PlanEditRoute) },
                        onRerunSetup = { nav.navigate(SetupRoute) },
                    )
                }
                composable<PlanEditRoute> { PlanEditScreen(onBack = { nav.popBackStack() }) }
                composable<ScanRoute> {
                    ScanScreen(
                        onBack = { nav.popBackStack() },
                        onDraftReady = {
                            nav.navigate(RoEditRoute(fromDraft = true)) { popUpTo<ScanRoute> { inclusive = true } }
                        },
                        onManual = { nav.navigate(RoEditRoute()) { popUpTo<ScanRoute> { inclusive = true } } },
                    )
                }
                composable<RoEditRoute> { back ->
                    val r = back.toRoute<RoEditRoute>()
                    RoEditScreen(
                        roId = r.roId,
                        fromDraft = r.fromDraft,
                        onBack = { nav.popBackStack() },
                        onSaved = { id ->
                            if (r.roId == 0L) {
                                nav.navigate(RoDetailRoute(id)) { popUpTo<RoEditRoute> { inclusive = true } }
                            } else {
                                nav.popBackStack()
                            }
                        },
                        onOpenExisting = { id -> nav.navigate(RoDetailRoute(id)) { popUpTo<RoEditRoute> { inclusive = true } } },
                    )
                }
                composable<RoDetailRoute> { back ->
                    val r = back.toRoute<RoDetailRoute>()
                    RoDetailScreen(r.roId, onBack = { nav.popBackStack() }, onEditRo = { nav.navigate(RoEditRoute(roId = it)) })
                }
            }
        }
    }
}
