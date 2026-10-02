package io.github.manojppatil.offlinepay.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Indigo = Color(0xFF2F4A8A)
private val IndigoLight = Color(0xFFB4C5FF)

@Composable
fun CollectTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = IndigoLight)
    } else {
        lightColorScheme(primary = Indigo)
    }
    MaterialTheme(colorScheme = colors, content = content)
}
