package com.jeremy.flaghours.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jeremy.flaghours.FlagHoursApp

@Composable
fun app(): FlagHoursApp = LocalContext.current.applicationContext as FlagHoursApp

/** Creates a ViewModel from the app container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (FlagHoursApp) -> VM): VM {
    val a = app()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(a) } })
}
