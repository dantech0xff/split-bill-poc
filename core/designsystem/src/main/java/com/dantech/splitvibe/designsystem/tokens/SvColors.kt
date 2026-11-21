package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Neutral grounds, vivid violet blocks and a lime accent, with ink text. Every colour is declared
// once, here; Material's roles below and SvPalette both point at these, so changing a value cannot
// leave a stale copy behind. ColorTokensTest checks that text stays readable on what it sits on.

/** The raw colours of the palette, light and dark. Screens never read these: they use the roles. */
internal object Swatch {
    val Ink = Color(0xFF111114)
    val Graphite = Color(0xFF5F5E66)
    val Slate = Color(0xFF76757E)
    val Mist = Color(0xFFC6C5CE)
    val Paper = Color(0xFFF4F4F1)
    val White = Color(0xFFFFFFFF)

    // Light grounds, from the page to the deepest container.
    val Ground = Color(0xFFF6F6F4)
    val Chalk = Color(0xFFFAFAF8)
    val Linen = Color(0xFFF0EFEA)
    val Sand = Color(0xFFE9E8E2)
    val PhotoSand = Color(0xFFE7E6E0)
    val Stone = Color(0xFFE1E0D9)
    val Pebble = Color(0xFFD9D8D2)

    // Dark grounds, from the deepest to the lightest.
    val Abyss = Color(0xFF0A0A0C)
    val Night = Color(0xFF0F0F12)
    val Coal = Color(0xFF1C1C21)
    val Smoke = Color(0xFF232329)
    val Charcoal = Color(0xFF26262C)
    val Iron = Color(0xFF2A2A31)
    val Steel = Color(0xFF33333B)
    val Seam = Color(0xFF3A3A42)
    val Fog = Color(0xFF8E8D97)
    val Haze = Color(0xFFB7B6BF)

    val Violet = Color(0xFF5B34F5)
    val VioletPale = Color(0xFFE9E3FF)
    val VioletDeep = Color(0xFF1E0A6B)
    val VioletNight = Color(0xFF2A1F7A)
    val VioletLight = Color(0xFFE4DCFF)

    val Lime = Color(0xFFC9F73A)
    val LimePale = Color(0xFFEEFBC2)
    val LimeNight = Color(0xFF2F3A0F)
    val LimeLight = Color(0xFFE9F7B0)

    val Brick = Color(0xFFB42A14)
    val BrickPale = Color(0xFFFBE3DE)
    val BrickDeep = Color(0xFF3B0F08)
    val Coral = Color(0xFFFF8A70)
    val CoralNight = Color(0xFF5A1A10)
    val CoralLight = Color(0xFFFFDAD3)

    // Money that comes back and money that is owed, as text.
    val Forest = Color(0xFF0A6B4E)
    val Rust = Color(0xFFA83508)
    val Jade = Color(0xFF5BE3B0)
    val Apricot = Color(0xFFFF9B5E)

    // The bar that draws money coming back, the same in both themes.
    val Emerald = Color(0xFF17B383)

    // The bright colours of people and categories. Ink text sits on all of them, in both themes.
    val Pink = Color(0xFFFF6FB5)
    val Orange = Color(0xFFFF8A3D)
    val Sky = Color(0xFF5CC8FF)
    val Yellow = Color(0xFFFFD23F)
    val Mint = Color(0xFF5BE3A0)
    val Lavender = Color(0xFFB9A2FF)
    val Teal = Color(0xFF7CE0D6)
    val Periwinkle = Color(0xFFA8B4FF)
}

internal val LightColors = with(Swatch) {
    lightColorScheme(
        primary = Violet,
        onPrimary = White,
        primaryContainer = VioletPale,
        onPrimaryContainer = VioletDeep,
        inversePrimary = Lime,
        secondary = Ink,
        onSecondary = White,
        // A light neutral, not ink: Material's tonal buttons and badges sit on it, and they must
        // stay lighter than the main action.
        secondaryContainer = Sand,
        onSecondaryContainer = Ink,
        tertiary = Lime,
        onTertiary = Ink,
        tertiaryContainer = LimePale,
        onTertiaryContainer = Ink,
        background = Ground,
        onBackground = Ink,
        surface = Ground,
        onSurface = Ink,
        surfaceVariant = Sand,
        onSurfaceVariant = Graphite,
        outline = Slate,
        outlineVariant = Pebble,
        inverseSurface = Ink,
        inverseOnSurface = Paper,
        error = Brick,
        onError = White,
        errorContainer = BrickPale,
        onErrorContainer = BrickDeep,
        surfaceDim = Stone,
        surfaceBright = White,
        surfaceContainerLowest = White,
        surfaceContainerLow = Chalk,
        surfaceContainer = Linen,
        surfaceContainerHigh = Sand,
        surfaceContainerHighest = Stone,
        surfaceTint = Violet,
        primaryFixed = VioletPale,
        primaryFixedDim = VioletPale,
        onPrimaryFixed = VioletDeep,
        onPrimaryFixedVariant = VioletDeep,
        secondaryFixed = Sand,
        secondaryFixedDim = Sand,
        onSecondaryFixed = Ink,
        onSecondaryFixedVariant = Ink,
        tertiaryFixed = LimePale,
        tertiaryFixedDim = LimePale,
        onTertiaryFixed = Ink,
        onTertiaryFixedVariant = Ink,
    )
}

internal val DarkColors = with(Swatch) {
    darkColorScheme(
        // On the dark ground the main action and links are lime; the violet blocks stay violet,
        // which is why the brand colour lives in SvPalette and not in primary.
        primary = Lime,
        onPrimary = Ink,
        primaryContainer = VioletNight,
        onPrimaryContainer = VioletLight,
        inversePrimary = Violet,
        secondary = Paper,
        onSecondary = Ink,
        secondaryContainer = Steel,
        onSecondaryContainer = Paper,
        tertiary = Lime,
        onTertiary = Ink,
        tertiaryContainer = LimeNight,
        onTertiaryContainer = LimeLight,
        background = Night,
        onBackground = Paper,
        surface = Night,
        onSurface = Paper,
        surfaceVariant = Iron,
        onSurfaceVariant = Haze,
        outline = Fog,
        outlineVariant = Seam,
        inverseSurface = Paper,
        inverseOnSurface = Ink,
        error = Coral,
        onError = Ink,
        errorContainer = CoralNight,
        onErrorContainer = CoralLight,
        // The dimmest and the brightest of the dark grounds, as the role names say.
        surfaceDim = Abyss,
        surfaceBright = Steel,
        surfaceContainerLowest = Abyss,
        surfaceContainerLow = Coal,
        surfaceContainer = Smoke,
        surfaceContainerHigh = Iron,
        surfaceContainerHighest = Steel,
        surfaceTint = Lime,
        // The fixed roles are the same in both themes, as Material defines them.
        primaryFixed = VioletPale,
        primaryFixedDim = VioletPale,
        onPrimaryFixed = VioletDeep,
        onPrimaryFixedVariant = VioletDeep,
        secondaryFixed = Sand,
        secondaryFixedDim = Sand,
        onSecondaryFixed = Ink,
        onSecondaryFixedVariant = Ink,
        tertiaryFixed = LimePale,
        tertiaryFixedDim = LimePale,
        onTertiaryFixed = Ink,
        onTertiaryFixedVariant = Ink,
    )
}
