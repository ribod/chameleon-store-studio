package org.jetbrains.kotlinconf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import org.jetbrains.kotlinconf.theme.KotlinConfTheme
import org.jetbrains.kotlinconf.ui.AgendaScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KotlinConfTheme {
                AgendaScreen()
            }
        }
    }
}
