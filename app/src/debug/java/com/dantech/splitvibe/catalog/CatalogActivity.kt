package com.dantech.splitvibe.catalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme

/** Shows every design system component on one page. Exists in debug builds only. */
class CatalogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SplitVibeTheme {
                CatalogScreen()
            }
        }
    }
}
