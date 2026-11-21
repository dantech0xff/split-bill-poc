package com.dantech.splitvibe.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.data.groups.GroupSummary
import com.dantech.splitvibe.designsystem.components.SvActionIcons
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.domain.BillCurrency
import com.dantech.splitvibe.text.resolve
import com.dantech.splitvibe.ui.common.ProxyWork

/**
 * The user's groups, such as housemates or a trip, each as a card with its name and the currency
 * its balances are kept in, or that it still waits for its owner to let the user in. The list of
 * groups carries no balance, so none shows here; a group shows its balances once it is opened. It
 * is one of the main screens, with its large title, the navigation bar the app passes as
 * [bottomBar] and no back button.
 */
@Composable
fun GroupsScreen(
    groups: List<GroupSummary>?,
    signedIn: Boolean,
    status: ProxyWork,
    myName: String,
    localCurrency: String,
    onCreate: (name: String, currency: String, myName: String) -> Unit,
    onOpen: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismissMessage: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    var creating by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            SvLargeTopBar(
                title = stringResource(R.string.groups_title),
                actions = {
                    if (signedIn) {
                        SvPrimaryButton(
                            text = stringResource(R.string.groups_new),
                            onClick = { creating = true },
                            icon = SvActionIcons.Plus,
                            compact = true,
                        )
                    }
                },
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        // The list ends above the navigation bar, so no group ever passes under it half hidden.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(top = SvSpacing.sm, bottom = SvSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
        ) {
            if (!signedIn) {
                item(key = "sign-in") {
                    SvMessageCard(
                        text = stringResource(R.string.groups_need_sign_in),
                        isError = false,
                        action = stringResource(R.string.common_sign_in_in_settings),
                        onAction = onOpenSettings,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    )
                }
            }
            status.message?.let { message ->
                item(key = "message") {
                    SvMessageCard(
                        text = message.resolve(),
                        isError = true,
                        action = stringResource(R.string.common_ok),
                        onAction = onDismissMessage,
                        modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    )
                }
            }
            if (status.working && groups == null) item(key = "loading") { SvLoadingIndicator(modifier = Modifier.padding(SvSpacing.xl)) }
            if (signedIn && groups?.isEmpty() == true) {
                item(key = "empty") {
                    SvEmptyState(
                        icon = Icons.Default.Person,
                        text = stringResource(R.string.groups_empty),
                    )
                }
            }
            items(groups.orEmpty(), key = { group -> group.id }) { group -> GroupCard(group = group, onOpen = { onOpen(group.id) }) }
        }
    }

    if (creating) {
        NewGroupSheet(
            myName = myName,
            localCurrency = localCurrency,
            onCreate = { name, currency, me ->
                creating = false
                onCreate(name, currency, me)
            },
            onDismiss = { creating = false },
        )
    }
}

/**
 * One group as a card of its own: its letters, its name, and the currency its balances are kept in,
 * or a tag that says it waits for its owner. A screen reader reads the card as one button.
 */
@Composable
private fun GroupCard(group: GroupSummary, onOpen: () -> Unit) {
    val detail = if (group.isPending) stringResource(R.string.groups_waiting_for_owner) else group.currency.code
    SvListRow(
        position = SvRowPosition.Single,
        label = stringResource(R.string.groups_row_label, group.name, detail),
        onClickLabel = stringResource(R.string.common_open),
        onClick = onOpen,
    ) {
        SvAvatar(group.name, size = GROUP_AVATAR_SIZE)
        Spacer(Modifier.width(SvSpacing.md))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SvSpacing.xs)) {
            Text(group.name, style = MaterialTheme.typography.bodyLargeEmphasized)
            if (group.isPending) {
                SvTag(detail)
            } else {
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(SvSpacing.sm))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun NewGroupSheet(myName: String, localCurrency: String, onCreate: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(localCurrency) }
    var me by rememberSaveable { mutableStateOf(myName) }
    val valid = name.isNotBlank() && me.isNotBlank() && BillCurrency.isKnownCode(currency)
    SvBottomSheet(
        title = stringResource(R.string.groups_new),
        supportingText = stringResource(R.string.groups_new_hint),
        onDismiss = onDismiss,
        actions = {
            SvQuietButton(stringResource(R.string.common_cancel), onClick = onDismiss, modifier = Modifier.weight(1f))
            SvPrimaryButton(
                text = stringResource(R.string.groups_make),
                onClick = { onCreate(name, currency, me) },
                modifier = Modifier.weight(1f),
                enabled = valid,
            )
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = SvSpacing.screen), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            SvTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.groups_name),
                modifier = Modifier.fillMaxWidth(),
                maxLength = NAME_LENGTH,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            SvTextField(
                value = currency,
                // In capitals and three letters long, also where a capital letter is longer than its small one.
                onValueChange = { currency = it.uppercase().take(CURRENCY_LENGTH) },
                label = stringResource(R.string.editor_currency),
                modifier = Modifier.fillMaxWidth(),
                isError = !BillCurrency.isKnownCode(currency),
                supportingText = stringResource(R.string.groups_currency_hint),
                maxLength = CURRENCY_LENGTH,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
            )
            SvTextField(
                value = me,
                onValueChange = { me = it },
                label = stringResource(R.string.groups_your_name),
                modifier = Modifier.fillMaxWidth(),
                maxLength = NAME_LENGTH,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        }
    }
}

internal const val NAME_LENGTH = 60
private const val CURRENCY_LENGTH = 3
private val GROUP_AVATAR_SIZE = 48.dp
