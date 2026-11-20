package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dantech.splitvibe.designsystem.R

// Bricolage Grotesque for titles and amounts, Be Vietnam Pro for everything else; both cover
// Vietnamese in full. The files ship with the app, so text looks the same on every phone.
// font-checksums.sha256 in this module records where each file comes from and its hash.

/** Titles and amounts. Compose picks a file within a family by weight, never by size. */
internal val DisplayFamily = FontFamily(
    Font(R.font.bricolage_grotesque_bold, FontWeight.Bold),
    Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold),
)

/**
 * The cut of Bricolage Grotesque drawn for large sizes, which is narrower. It is a family of its own
 * because it has the same weight as the ExtraBold in [DisplayFamily], and is used only from 56sp.
 */
internal val DisplayHeroFamily = FontFamily(
    Font(R.font.bricolage_grotesque_display_extrabold, FontWeight.ExtraBold),
)

/** Body text, labels and small titles. It has no digits of equal width, so amounts never use it. */
internal val TextFamily = FontFamily(
    Font(R.font.be_vietnam_pro_regular, FontWeight.Normal),
    Font(R.font.be_vietnam_pro_medium, FontWeight.Medium),
    Font(R.font.be_vietnam_pro_semibold, FontWeight.SemiBold),
    Font(R.font.be_vietnam_pro_bold, FontWeight.Bold),
)

// Large titles are set a little tighter than small ones.
private val TrackingTightest = (-0.03).em
private val TrackingTighter = (-0.02).em
private val TrackingTight = (-0.01).em
private val TrackingNone = 0.em

/** Material's own scale: each style here is a copy of its style, so it keeps how lines are placed. */
private val Material = Typography()

private fun TextStyle.restyle(family: FontFamily, weight: FontWeight, size: Int, lineHeight: Int, tracking: TextUnit) =
    copy(fontFamily = family, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = tracking)

private fun TextStyle.withEqualWidthDigits() = copy(fontFeatureSettings = "tnum")

// Titles: Bricolage Grotesque ExtraBold. An emphasized style cannot be any heavier, so it equals its plain style.
private val DisplayLarge = Material.displayLarge.restyle(DisplayHeroFamily, FontWeight.ExtraBold, 56, 60, TrackingTightest)
private val DisplayMedium = Material.displayMedium.restyle(DisplayFamily, FontWeight.ExtraBold, 38, 42, TrackingTightest)
private val DisplaySmall = Material.displaySmall.restyle(DisplayFamily, FontWeight.ExtraBold, 32, 36, TrackingTighter)
private val HeadlineLarge = Material.headlineLarge.restyle(DisplayFamily, FontWeight.ExtraBold, 30, 36, TrackingTighter)
private val HeadlineMedium = Material.headlineMedium.restyle(DisplayFamily, FontWeight.ExtraBold, 26, 32, TrackingTighter)
private val HeadlineSmall = Material.headlineSmall.restyle(DisplayFamily, FontWeight.ExtraBold, 24, 30, TrackingTighter)
private val TitleLarge = Material.titleLarge.restyle(DisplayFamily, FontWeight.ExtraBold, 20, 26, TrackingTight)

// Everything else: Be Vietnam Pro, one weight heavier when emphasized.
private fun text(base: TextStyle, weight: FontWeight, size: Int, lineHeight: Int) =
    base.restyle(TextFamily, weight, size, lineHeight, TrackingNone)

/** The type scale of Split Vibe, in Material's 15 roles and their 15 emphasized versions. */
internal val SvTypography = Typography(
    displayLarge = DisplayLarge,
    displayMedium = DisplayMedium,
    displaySmall = DisplaySmall,
    headlineLarge = HeadlineLarge,
    headlineMedium = HeadlineMedium,
    headlineSmall = HeadlineSmall,
    titleLarge = TitleLarge,
    titleMedium = text(Material.titleMedium, FontWeight.SemiBold, 16, 22),
    titleSmall = text(Material.titleSmall, FontWeight.SemiBold, 14, 20),
    bodyLarge = text(Material.bodyLarge, FontWeight.Normal, 16, 24),
    bodyMedium = text(Material.bodyMedium, FontWeight.Normal, 15, 22),
    bodySmall = text(Material.bodySmall, FontWeight.Normal, 13, 18),
    labelLarge = text(Material.labelLarge, FontWeight.SemiBold, 14, 20),
    labelMedium = text(Material.labelMedium, FontWeight.SemiBold, 12, 18),
    labelSmall = text(Material.labelSmall, FontWeight.SemiBold, 11, 16),
    displayLargeEmphasized = DisplayLarge,
    displayMediumEmphasized = DisplayMedium,
    displaySmallEmphasized = DisplaySmall,
    headlineLargeEmphasized = HeadlineLarge,
    headlineMediumEmphasized = HeadlineMedium,
    headlineSmallEmphasized = HeadlineSmall,
    titleLargeEmphasized = TitleLarge,
    titleMediumEmphasized = text(Material.titleMediumEmphasized, FontWeight.Bold, 16, 22),
    titleSmallEmphasized = text(Material.titleSmallEmphasized, FontWeight.Bold, 14, 20),
    bodyLargeEmphasized = text(Material.bodyLargeEmphasized, FontWeight.SemiBold, 16, 24),
    bodyMediumEmphasized = text(Material.bodyMediumEmphasized, FontWeight.SemiBold, 15, 22),
    bodySmallEmphasized = text(Material.bodySmallEmphasized, FontWeight.SemiBold, 13, 18),
    labelLargeEmphasized = text(Material.labelLargeEmphasized, FontWeight.Bold, 14, 20),
    labelMediumEmphasized = text(Material.labelMediumEmphasized, FontWeight.Bold, 12, 18),
    labelSmallEmphasized = text(Material.labelSmallEmphasized, FontWeight.Bold, 11, 16),
)

// Amounts of money are the main thing on most screens. They are set in Bricolage Grotesque with
// digits of equal width, so that a column of amounts lines up and a changing number does not jump.
// They are the same whatever Typography they are read from: a nested theme does not change them.

private val AmountHero = DisplayLarge.withEqualWidthDigits()
private val AmountLarge = Material.headlineSmall.restyle(DisplayFamily, FontWeight.ExtraBold, 24, 28, TrackingTighter)
    .withEqualWidthDigits()
private val AmountMedium = Material.titleLarge.restyle(DisplayFamily, FontWeight.ExtraBold, 19, 24, TrackingTight)
    .withEqualWidthDigits()
private val AmountSmall = Material.bodyLarge.restyle(DisplayFamily, FontWeight.Bold, 15, 20, TrackingNone)
    .withEqualWidthDigits()

/** The one amount a whole screen is built around, in its hero block, such as what friends owe you. */
val Typography.amountHero: TextStyle get() = AmountHero

/** The one amount a screen is about, such as the bill total. */
val Typography.amountLarge: TextStyle get() = AmountLarge

/** An amount in a list row that the row is about, such as what a person pays. */
val Typography.amountMedium: TextStyle get() = AmountMedium

/** An amount that supports something else, such as the price of one item. */
val Typography.amountSmall: TextStyle get() = AmountSmall
