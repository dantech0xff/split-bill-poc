package com.dantech.splitvibe.domain

import java.util.Locale

/**
 * The locale that numbers, amounts of money and dates are written in wherever no other is given:
 * English, the language of the app's texts and of the domain's own sentences. So they read the same
 * as the rest of those texts whatever the phone is set to: the digits 0 to 9, "." before decimals,
 * "," between thousands, and English month and day names.
 */
val TEXT_LOCALE: Locale = Locale.US
