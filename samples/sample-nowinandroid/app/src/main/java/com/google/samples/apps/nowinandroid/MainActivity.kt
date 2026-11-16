package com.google.samples.apps.nowinandroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.google.samples.apps.nowinandroid.core.designsystem.theme.NiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NiaTheme {
                NiaApp()
            }
        }
    }
}
