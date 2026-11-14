package com.dantech.splitvibe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.dantech.splitvibe.ui.AppNavHost
import com.dantech.splitvibe.ui.theme.SplitBillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SplitBillApp).container
        setContent {
            SplitBillTheme {
                AppNavHost(container)
            }
        }
    }
}
