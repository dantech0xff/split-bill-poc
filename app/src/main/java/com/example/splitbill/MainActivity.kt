package com.example.splitbill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.splitbill.ui.AppNavHost
import com.example.splitbill.ui.theme.SplitBillTheme

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
