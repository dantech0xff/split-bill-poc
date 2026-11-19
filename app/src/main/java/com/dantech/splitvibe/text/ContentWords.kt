package com.dantech.splitvibe.text

import android.content.res.Resources
import com.dantech.splitvibe.R

/**
 * Words the app writes into what it keeps on the phone or sends to other people, rather than shows
 * on a screen: the user in the list of the people on a bill, the name of a recurring bill or a line
 * of a bill page that has none, and whom a request is to or from when that has no name. They are
 * read from the resources once, in the language the app starts in: what was kept or sent stays as it
 * was written.
 */
class ContentWords(
    /** The user among the people on a bill. */
    val you: String,
    /** Someone on a bill page without a name. */
    val someone: String,
    /** A recurring bill without a name. */
    val bill: String,
    /** An item on a bill page without a name. */
    val item: String,
    /** A charge on a bill page without a name. */
    val charge: String,
    /** The one line of a bill page for the charges past the most it shows. */
    val otherCharges: String,
    /** Whom a request greets when the person has no name, as in "Hi there!". */
    val payer: String,
    /** Whom a request comes from when the user gave no name. */
    val creditor: String,
) {
    companion object {
        fun of(resources: Resources) = ContentWords(
            you = resources.getString(R.string.common_you),
            someone = resources.getString(R.string.common_someone),
            bill = resources.getString(R.string.common_bill),
            item = resources.getString(R.string.common_item),
            charge = resources.getString(R.string.content_charge),
            otherCharges = resources.getString(R.string.content_other_charges),
            payer = resources.getString(R.string.content_payer),
            creditor = resources.getString(R.string.content_creditor),
        )
    }
}
