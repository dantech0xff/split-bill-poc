package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours of Split Vibe that Material's colour roles have no place for: the brand blocks, the
 * lime accent, the floating bars, money coming in and going out, people and categories.
 * Read them with `SvTheme.palette`.
 */
@Immutable
class SvPalette internal constructor(
    /** The violet of the large blocks, such as the hero of a screen. The same in both themes. */
    val brand: Color,
    /** Text and icons on [brand]. */
    val onBrand: Color,
    /** The lime of solid accent blocks, tags and ticks. The same in both themes. */
    val accent: Color,
    /** Text and icons on [accent]. */
    val onAccent: Color,
    /** The floating bars at the bottom of a screen. */
    val bar: Color,
    /** Text and icons on [bar]. */
    val onBar: Color,
    /** Secondary text and icons on [bar], such as a tab that is not selected. */
    val onBarQuiet: Color,
    /** The thin edge a floating bar draws around itself; it shows only in the dark theme. */
    val barOutline: Color,
    /** Text for money that comes back to someone: "gets back". */
    val moneyIn: Color,
    /** Text for money someone owes: "owes". */
    val moneyOut: Color,
    /**
     * The bar that draws money coming back to someone beside the largest balance, such as on the
     * balances of a group, the same green in both themes. The amount beside it says the same in
     * words, in [moneyIn].
     */
    val moneyInBar: Color,
    /** The colours a person's initials sit on, in a fixed order. */
    val person: List<Color>,
    /** Text and icons on the bright colours: [person], [category] and [notice]. */
    val onPerson: Color,
    /** The colour of each bill category, in the order the categories are declared. */
    val category: List<Color>,
    /** The tile behind the icon of a notice. */
    val notice: Color,
    /** The ground a bill's photo sits on. */
    val photoZone: Color,
    /** The ring of a choice of one that is not chosen, which shows on the page and on a card. */
    val radioRing: Color,
    /**
     * The tile of something that stands for none of a kind, such as the bills in no group: a grey that
     * stands a step further out from what it lies on than a quiet control, with an icon in the colour
     * of text.
     */
    val blankTile: Color,
)

private val PersonColors = with(Swatch) { listOf(Pink, Orange, Sky, Yellow, Mint, Lavender, Teal) }

// Food and drink, groceries, transport, lodging, entertainment, shopping, utilities, rent, other.
private val CategoryColors = with(Swatch) { listOf(Orange, Mint, Sky, Lavender, Pink, Yellow, Teal, Periwinkle, Pebble) }

internal val LightPalette = with(Swatch) {
    SvPalette(
        brand = Violet,
        onBrand = White,
        accent = Lime,
        onAccent = Ink,
        bar = Ink,
        onBar = White,
        onBarQuiet = Mist,
        barOutline = Ink,
        moneyIn = Forest,
        moneyOut = Rust,
        moneyInBar = Emerald,
        person = PersonColors,
        onPerson = Ink,
        category = CategoryColors,
        notice = Yellow,
        photoZone = PhotoSand,
        radioRing = Pewter,
        blankTile = Pebble,
    )
}

internal val DarkPalette = with(Swatch) {
    SvPalette(
        brand = Violet,
        onBrand = White,
        accent = Lime,
        onAccent = Ink,
        bar = Charcoal,
        onBar = Paper,
        onBarQuiet = Haze,
        barOutline = Seam,
        moneyIn = Jade,
        moneyOut = Apricot,
        moneyInBar = Emerald,
        person = PersonColors,
        onPerson = Ink,
        category = CategoryColors,
        notice = Yellow,
        photoZone = Iron,
        radioRing = Fog,
        blankTile = Seam,
    )
}

/** Provided once by `SplitVibeTheme`, light or dark with the rest of the theme. */
internal val LocalSvPalette = staticCompositionLocalOf { LightPalette }

/** The parts of the Split Vibe theme that `MaterialTheme` does not carry. */
object SvTheme {
    /** The palette of the current theme. */
    val palette: SvPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalSvPalette.current
}
