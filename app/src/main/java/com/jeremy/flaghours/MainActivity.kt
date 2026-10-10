package com.jeremy.flaghours

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jeremy.flaghours.ui.navigation.AppNav
import com.jeremy.flaghours.ui.theme.FlagHoursTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { FlagHoursTheme { AppNav() } }
    }
}
