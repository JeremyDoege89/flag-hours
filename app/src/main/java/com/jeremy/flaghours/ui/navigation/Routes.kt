package com.jeremy.flaghours.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object SetupRoute
@Serializable object DashboardRoute
@Serializable object JobsRoute
@Serializable object TimeRoute
@Serializable object PaycheckRoute
@Serializable object SettingsRoute
@Serializable object PlanEditRoute
@Serializable object ScanRoute
@Serializable data class RoEditRoute(val roId: Long = 0, val fromDraft: Boolean = false)
@Serializable data class RoDetailRoute(val roId: Long)
