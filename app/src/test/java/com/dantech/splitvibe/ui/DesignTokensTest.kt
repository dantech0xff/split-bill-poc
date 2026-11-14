package com.dantech.splitvibe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dantech.splitvibe.designsystem.theme.SplitVibeTheme
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws the design tokens that cannot be judged from numbers: the four elevation levels and the
 * text styles for amounts. The images are compared with the references like the screens are.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h640dp-xhdpi")
class DesignTokensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `elevation and amount styles in the light theme`() = capture("tokens-light", darkTheme = false)

    @Test
    fun `elevation and amount styles in the dark theme`() = capture("tokens-dark", darkTheme = true)

    private fun capture(name: String, darkTheme: Boolean) {
        compose.setContent { SplitVibeTheme(darkTheme = darkTheme) { TokenSheet() } }
        compose.waitForIdle()
        captureScreenRoboImage(File("src/test/screenshots/$name.png"))
    }
}

@Composable
private fun TokenSheet() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        SvElevation.entries.forEach { level ->
            val shape = MaterialTheme.shapes.large
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .elevation(level, shape)
                    .background(level.surface, shape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(level.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            val color = MaterialTheme.colorScheme.onSurface
            Text("₫902,664", style = MaterialTheme.typography.amountLarge, color = color)
            Text("₫111,111", style = MaterialTheme.typography.amountMedium, color = color)
            Text("₫24,000", style = MaterialTheme.typography.amountSmall, color = color)
        }
    }
}
