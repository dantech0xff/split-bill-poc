package com.dantech.splitvibe.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.dantech.splitvibe.designsystem.components.SvCategoryIcons
import com.dantech.splitvibe.designsystem.tokens.SvTheme
import com.dantech.splitvibe.domain.BillCategory

/**
 * How a category of bills looks wherever a bill or its spending shows it: the [icon] of what it was
 * spent on, and the category's [color] from the palette. Show the two together on an `SvIconTile`,
 * beside the category's name.
 */
@Immutable
internal class CategoryStyle(val icon: ImageVector, val color: Color)

/**
 * The look of this category in the current theme. The palette lists the colours of the categories
 * in the order they are declared, so a category's colour is the one at its place in that order.
 */
internal val BillCategory.style: CategoryStyle
    @Composable
    @ReadOnlyComposable
    get() = CategoryStyle(icon = icon, color = SvTheme.palette.category[ordinal])

private val BillCategory.icon: ImageVector
    get() = when (this) {
        BillCategory.FOOD_AND_DRINK -> SvCategoryIcons.FoodAndDrink
        BillCategory.GROCERIES -> SvCategoryIcons.Groceries
        BillCategory.TRANSPORT -> SvCategoryIcons.Transport
        BillCategory.LODGING -> SvCategoryIcons.Lodging
        BillCategory.ENTERTAINMENT -> SvCategoryIcons.Entertainment
        BillCategory.SHOPPING -> SvCategoryIcons.Shopping
        BillCategory.UTILITIES -> SvCategoryIcons.Utilities
        BillCategory.RENT -> SvCategoryIcons.Rent
        BillCategory.OTHER -> SvCategoryIcons.Other
    }
