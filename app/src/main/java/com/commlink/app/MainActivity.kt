package com.commlink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.commlink.app.ui.navigation.CommLinkNavigation
import com.commlink.app.ui.theme.CommLinkTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CommLinkTheme {
                CommLinkNavigation()
            }
        }
    }
}
