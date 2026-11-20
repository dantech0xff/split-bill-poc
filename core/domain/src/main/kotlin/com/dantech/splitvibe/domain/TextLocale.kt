package com.dantech.splitvibe.domain

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.IllformedLocaleException
import java.util.Locale

/**
 * The locale of the app's words: English, the language of its texts and of the domain's own
 * sentences. Its rules decide the plurals and the counts written inside a sentence, and it names the
 * months and the days of the week, whatever the phone is set to. Amounts, other numbers and the order
 * of a date follow the phone's region instead, as [regionLocale] gives it.
 */
val TEXT_LOCALE: Locale = Locale.US

/**
 * The locale amounts, numbers and dates are written for: the region the phone is set to, so that an
 * amount is "902.664 ₫" on a phone set to Vietnam and "₫902,664" on one set to the United States. It
 * is read when it is asked for, so a phone whose region changes writes the new way from then on, and
 * it is the same object for as long as the phone's region stays the same. Its digits are always 0 to
 * 9, those of the app's English texts, also in a region that writes numbers with digits of its own,
 * such as Egypt or Iran. A phone locale that is not well formed gives [TEXT_LOCALE], so that no
 * amount fails to be written over it.
 */
fun regionLocale(): Locale {
    val phone = Locale.getDefault(Locale.Category.FORMAT)
    lastRegion?.let { (of, region) -> if (of == phone) return region }
    val region = try {
        Locale.Builder().setLocale(phone).setUnicodeLocaleKeyword("nu", "latn").build()
    } catch (e: IllformedLocaleException) {
        TEXT_LOCALE
    }
    lastRegion = phone to region
    return region
}

// An amount that runs through the numbers in between is written on every frame, and a currency keeps
// its formatter for as long as it is asked for the same locale. So the region is built once for each
// locale the phone is set to, and is the same object from then on. A thread that reads an older pair
// builds the region again, which is all that a race between two threads costs.
@Volatile
private var lastRegion: Pair<Locale, Locale>? = null

/**
 * [value] as [locale], the phone's region unless another is given, writes a number: with its marks
 * between thousands and before decimals, such as "2.8" and "1,500" in the United States or "2,8" and
 * "1.500" in Vietnam, and without zeros at the end of its decimals that say nothing.
 */
fun numberText(value: BigDecimal, locale: Locale = regionLocale()): String {
    val shown = value.stripTrailingZeros()
    return NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = shown.scale().coerceAtLeast(0) }.format(shown)
}

/**
 * [value] as a field shows a number other than money to be edited, such as a quantity or a rate:
 * with the decimal mark of [locale], the phone's region unless another is given, and without
 * grouping, such as "1234.5" or "1234,5". An amount of money is written for its field by
 * [BillCurrency.inputText], with the mark that money takes.
 */
fun inputNumberText(value: BigDecimal, locale: Locale = regionLocale()): String =
    fieldText(value, DecimalFormatSymbols.getInstance(locale).decimalSeparator)

/**
 * [value] without grouping and with [mark] before its decimals. Whatever reads a typed number, such
 * as [BillCurrency.parse], takes both "." and "," for the decimal mark, so it reads this back as
 * [value]; any other mark a region may have gives ".".
 */
internal fun fieldText(value: BigDecimal, mark: Char): String {
    val readable = mark.takeIf { it == '.' || it == ',' } ?: '.'
    return value.toPlainString().replace('.', readable)
}
