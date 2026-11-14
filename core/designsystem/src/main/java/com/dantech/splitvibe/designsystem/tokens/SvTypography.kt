package com.dantech.splitvibe.designsystem.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle

/** The Material 3 type scale as it ships, including its emphasized styles. */
internal val SvTypography = Typography()

// Amounts of money are the main thing on most screens. They use the emphasized weight, and digits
// of equal width so that a column of amounts lines up and a changing number does not jump sideways.

/** The one amount a screen is about, such as the bill total. */
val Typography.amountLarge: TextStyle get() = headlineMediumEmphasized.withEqualWidthDigits()

/** An amount in a list row that the row is about, such as what a person pays. */
val Typography.amountMedium: TextStyle get() = titleMediumEmphasized.withEqualWidthDigits()

/** An amount that supports something else, such as the price of one item. */
val Typography.amountSmall: TextStyle get() = bodyLarge.withEqualWidthDigits()

private fun TextStyle.withEqualWidthDigits() = copy(fontFeatureSettings = "tnum")
