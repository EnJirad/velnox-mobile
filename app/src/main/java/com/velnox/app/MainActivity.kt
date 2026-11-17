package com.velnox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.velnox.app.feature.auth.LoginScreen
import com.velnox.app.ui.theme.VelnoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            VelnoxTheme {
                LoginScreen()
            }
        }
    }
}
