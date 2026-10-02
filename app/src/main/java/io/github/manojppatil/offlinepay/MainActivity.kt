package io.github.manojppatil.offlinepay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.manojppatil.offlinepay.ui.CollectTheme
import io.github.manojppatil.offlinepay.ui.PaymentsRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CollectTheme {
                PaymentsRoute()
            }
        }
    }
}
