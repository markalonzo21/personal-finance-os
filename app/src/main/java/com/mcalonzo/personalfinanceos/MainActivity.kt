package com.mcalonzo.personalfinanceos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalFinanceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = OLEDColors.bgBase
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Personal Finance OS",
                            color = OLEDColors.textPrimary
                        )
                    }
                }
            }
        }
    }
}
