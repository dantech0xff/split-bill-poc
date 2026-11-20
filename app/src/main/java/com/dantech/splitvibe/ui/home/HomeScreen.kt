package com.dantech.splitvibe.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.bills.BillListing
import com.dantech.splitvibe.designsystem.components.SvAccountButton
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvAvatarStack
import com.dantech.splitvibe.designsystem.components.SvBrandTopBar
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroAmount
import com.dantech.splitvibe.designsystem.components.SvLinkButton
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTagKind
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTile
import com.dantech.splitvibe.designsystem.components.SvTileIcons
import com.dantech.splitvibe.designsystem.components.SvTileKind
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.monthText
import com.dantech.splitvibe.text.plural
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.history.BillListingRow
import java.time.LocalDate

/**
 * The app's home. At the top is the one thing the user most needs to know, as [summary] works it
 * out: what friends owe them, what they owe, their share of this month's bills, or on a phone that
 * keeps no bill yet the invitation to split a first one. Below it, as tiles, this month's spending
 * when the block is about the requests, and [group], the group kept last; then the bills split last,
 * with the way to all of them. A new split starts from the navigation bar, which the app passes as
 * [bottomBar], or from the invitation.
 */
@Composable
fun HomeScreen(
    summary: HomeSummary,
    /** The group the phone kept last, with the user's balance in it; null shows no tile for it. */
    group: GroupTile?,
    /** The bills kept on the phone, newest first, of which the first few are shown; null until they are read. */
    bills: List<BillListing>?,
    onOpenBill: (String) -> Unit,
    onSeeAllBills: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onSplitBill: () -> Unit,
    /** The name the user goes by, whose first letter the account button shows. */
    myName: String = "",
    /**
     * Whether the phone keeps a recurring bill, which stays when the bills it came from are deleted.
     * The list of bills then still opens, as recurring bills are one of its tabs.
     */
    hasRecurringBills: Boolean = false,
    /** The day the bills' dates are written as of. */
    today: LocalDate = LocalDate.now(),
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            SvBrandTopBar(
                name = stringResource(R.string.app_name),
                actions = { SvAccountButton(name = myName, label = stringResource(R.string.settings_title), onClick = onOpenSettings) },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        // The list ends above the navigation bar, so no row ever passes under it half hidden.
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            summary.main?.let { main ->
                item(key = "main") {
                    MainBlock(
                        block = main,
                        onOpenRequests = onOpenRequests,
                        onSplitBill = onSplitBill,
                        modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.sm),
                    )
                }
            }
            if (summary.month != null || group != null) {
                item(key = "tiles") {
                    Tiles(
                        month = summary.month,
                        group = group,
                        onOpenInsights = onOpenInsights,
                        onOpenGroup = onOpenGroup,
                        modifier = Modifier.padding(start = SvSpacing.screen, end = SvSpacing.screen, top = SvSpacing.lg),
                    )
                }
            }
            item(key = "recent") {
                SvSectionHeader(
                    title = stringResource(R.string.home_recent_bills),
                    action = if (!bills.isNullOrEmpty() || hasRecurringBills) {
                        { SvTextAction(stringResource(R.string.home_see_all), onClick = onSeeAllBills) }
                    } else {
                        null
                    },
                )
            }
            // Bills still being read show nothing in their place, which would say there are none.
            if (bills?.isEmpty() == true) {
                item(key = "none") {
                    SvEmptyState(icon = Icons.AutoMirrored.Filled.List, text = stringResource(R.string.home_no_bills))
                }
            }
            val recent = bills.orEmpty().take(RECENT_BILLS)
            itemsIndexed(recent, key = { _, listing -> listing.id }) { index, listing ->
                BillListingRow(
                    listing = listing,
                    position = SvRowPosition.of(index, recent.size),
                    onOpen = { onOpenBill(listing.id) },
                    today = today,
                )
            }
        }
    }
}

@Composable
private fun MainBlock(block: HomeBlock, onOpenRequests: () -> Unit, onSplitBill: () -> Unit, modifier: Modifier) {
    // A screen reader reads what a block says as one element, and a button in it as one more.
    val oneElement = modifier.semantics(mergeDescendants = true) {}
    when (block) {
        is HomeBlock.FriendsOweYou -> RequestsHero(stringResource(R.string.home_friends_owe_you), block.owed, oneElement) {
            // What the user owes in turn, as the way to the requests to pay.
            block.youOwe?.let { owing ->
                val amounts = inSentence((listOf(owing.main) + owing.others).map { it.text })
                SvLinkButton(youOweText(amounts, owing.people), onClick = onOpenRequests, emphasis = amounts)
            }
        }

        // The whole block leads to the requests to pay, as the line under what friends owe does.
        is HomeBlock.YouOwe -> RequestsHero(
            label = stringResource(R.string.home_you_owe),
            requests = block.owing,
            modifier = modifier,
            onClick = onOpenRequests,
            onClickLabel = stringResource(R.string.home_open_requests),
        )

        is HomeBlock.ThisMonth -> SvHero(
            modifier = oneElement,
            label = { Text(monthText(block.spending.month)) },
            amount = { HeroAmount(block.spending.myShare) },
        ) {
            Text(plural(R.plurals.home_your_share_of, block.spending.bills).resolve())
        }

        HomeBlock.Invitation -> SvHero(modifier = oneElement) {
            Text(stringResource(R.string.home_intro_title), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(R.string.home_intro_text))
            // The words show, and no description goes with them: the round button of the navigation
            // bar is the one element that says "Split a bill" to a screen reader.
            SvPrimaryButton(stringResource(R.string.home_split_a_bill), onClick = onSplitBill, icon = SvActionIcons.Plus, compact = true)
        }
    }
}

/**
 * The open [requests] of one side under [label]: how many, their total, the totals in other
 * currencies, and who is on the other side. With [onClick] the whole block is one button.
 */
@Composable
private fun RequestsHero(
    label: String,
    requests: OpenRequests,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    below: @Composable ColumnScope.() -> Unit = {},
) {
    SvHero(
        modifier = modifier,
        label = {
            Text(label, modifier = Modifier.weight(1f))
            SvTag(plural(R.plurals.home_requests, requests.count).resolve(), kind = SvTagKind.Accent)
        },
        amount = { HeroAmount(requests.main) },
        onClick = onClick,
        onClickLabel = onClickLabel,
    ) {
        // Nothing converts the currency of a request, so each other currency keeps a total of its own.
        if (requests.others.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                requests.others.forEach { SvTag(it.text) }
            }
        }
        if (requests.people.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                SvAvatarStack(requests.people.take(FACES))
                Text(namesOf(requests.people), modifier = Modifier.weight(1f))
            }
        }
        below()
    }
}

/**
 * The tile of this month's spending, which leads to the insights, and that of [group], which opens
 * it, side by side; one alone takes the whole row.
 */
@Composable
private fun Tiles(month: MonthSpending?, group: GroupTile?, onOpenInsights: () -> Unit, onOpenGroup: (String) -> Unit, modifier: Modifier) {
    // Not stretched to one height by intrinsic measurement: that measures the values unshrunk, wrapped
    // onto more lines, and would leave both tiles far taller than what they hold.
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        val tile = Modifier.weight(1f)
        if (month != null) {
            SvTile(
                label = monthText(month.month),
                value = month.myShare.text,
                kind = SvTileKind.Accent,
                modifier = tile,
                caption = plural(R.plurals.home_your_share_of, month.bills).resolve(),
                trailing = { Icon(SvTileIcons.Spending, contentDescription = null) },
                onClick = onOpenInsights,
                onClickLabel = stringResource(R.string.common_open),
            )
        }
        if (group != null) {
            SvTile(
                label = group.name,
                value = group.amount.text,
                kind = SvTileKind.Ink,
                modifier = tile,
                note = stringResource(group.standing),
                leading = { SvAvatar(group.name, size = GROUP_AVATAR_SIZE) },
                onClick = { onOpenGroup(group.id) },
                onClickLabel = stringResource(R.string.common_open),
            )
        }
    }
}

/**
 * [money] in large type, its symbol or code smaller beside the number, on whichever side the phone's
 * region puts it. A screen reader says it as it is written: saying an amount in words, such as
 * "651,671 dong", is left for later, as the app has no way to yet.
 */
@Composable
private fun HeroAmount(money: Money) {
    val parts = money.currency.formatParts(money.minor)
    SvHeroAmount(prefix = parts.prefix, number = parts.number, suffix = parts.suffix, spokenText = money.text)
}

/** "You owe ₫150,000 to Dana": what the user owes, [amounts] in every currency, and to whom, [people]. */
@Composable
private fun youOweText(amounts: String, people: List<String>): String {
    val whom = if (people.isEmpty()) stringResource(R.string.requests_them) else namesOf(people)
    return stringResource(R.string.home_you_owe_to, amounts, whom)
}

/** [people] in a sentence, such as "Minh and Lan", with the first ones only, and how many more, when they are many. */
@Composable
private fun namesOf(people: List<String>): String = if (people.size > MAX_NAMED) {
    val named = people.take(NAMED_OF_MANY).joinToString(", ")
    plural(R.plurals.home_and_others, people.size - NAMED_OF_MANY, named, people.size - NAMED_OF_MANY).resolve()
} else {
    inSentence(people)
}

/** [items] as a list in a sentence, such as "A, B and C". */
@Composable
private fun inSentence(items: List<String>): String = if (items.size < 2) {
    items.singleOrNull().orEmpty()
} else {
    stringResource(R.string.insights_and_list, items.dropLast(1).joinToString(", "), items.last())
}

/** How many of the latest bills the home screen shows; the rest are a tap away. */
private const val RECENT_BILLS = 3

/** The most people named in full; more are named as the first [NAMED_OF_MANY] and how many others. */
private const val MAX_NAMED = 3
private const val NAMED_OF_MANY = 2

/** The most avatars side by side; the names say who they all are. */
private const val FACES = 3

private val GROUP_AVATAR_SIZE = 28.dp
