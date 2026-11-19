package com.dantech.splitvibe.text

import androidx.annotation.StringRes
import com.dantech.splitvibe.R
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.domain.ChargeKind

/** What the app calls [this] category, such as "Food & drink". */
@get:StringRes
val BillCategory.labelRes: Int
    get() = when (this) {
        BillCategory.FOOD_AND_DRINK -> R.string.category_food_and_drink
        BillCategory.GROCERIES -> R.string.category_groceries
        BillCategory.TRANSPORT -> R.string.category_transport
        BillCategory.LODGING -> R.string.category_lodging
        BillCategory.ENTERTAINMENT -> R.string.category_entertainment
        BillCategory.SHOPPING -> R.string.category_shopping
        BillCategory.UTILITIES -> R.string.category_utilities
        BillCategory.RENT -> R.string.category_rent
        BillCategory.OTHER -> R.string.category_other
    }

/** What the app calls a charge of [this] kind, such as "Service charge". */
@get:StringRes
val ChargeKind.labelRes: Int
    get() = when (this) {
        ChargeKind.TAX -> R.string.charge_tax
        ChargeKind.SERVICE_CHARGE -> R.string.charge_service
        ChargeKind.TIP -> R.string.charge_tip
        ChargeKind.DISCOUNT -> R.string.charge_discount
        ChargeKind.ROUNDING -> R.string.charge_rounding
        ChargeKind.OTHER -> R.string.charge_other
    }
