package com.dantech.splitvibe.catalog

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dantech.splitvibe.R
import com.dantech.splitvibe.designsystem.components.SvAvatar
import com.dantech.splitvibe.designsystem.components.SvAvatarStack
import com.dantech.splitvibe.designsystem.components.SvBarChart
import com.dantech.splitvibe.designsystem.components.SvBillPhoto
import com.dantech.splitvibe.designsystem.components.SvBottomBar
import com.dantech.splitvibe.designsystem.components.SvBottomSheet
import com.dantech.splitvibe.designsystem.components.SvCard
import com.dantech.splitvibe.designsystem.components.SvChartBar
import com.dantech.splitvibe.designsystem.components.SvCheckbox
import com.dantech.splitvibe.designsystem.components.SvChoiceChips
import com.dantech.splitvibe.designsystem.components.SvChoiceGroup
import com.dantech.splitvibe.designsystem.components.SvDialog
import com.dantech.splitvibe.designsystem.components.SvEmptyState
import com.dantech.splitvibe.designsystem.components.SvFabMenu
import com.dantech.splitvibe.designsystem.components.SvFabMenuItem
import com.dantech.splitvibe.designsystem.components.SvFloatingToolbar
import com.dantech.splitvibe.designsystem.components.SvHero
import com.dantech.splitvibe.designsystem.components.SvHeroAmount
import com.dantech.splitvibe.designsystem.components.SvHeroKind
import com.dantech.splitvibe.designsystem.components.SvIconTile
import com.dantech.splitvibe.designsystem.components.SvLargeTopBar
import com.dantech.splitvibe.designsystem.components.SvListRow
import com.dantech.splitvibe.designsystem.components.SvLoadingIndicator
import com.dantech.splitvibe.designsystem.components.SvMenuOption
import com.dantech.splitvibe.designsystem.components.SvMessageCard
import com.dantech.splitvibe.designsystem.components.SvMeter
import com.dantech.splitvibe.designsystem.components.SvNavBar
import com.dantech.splitvibe.designsystem.components.SvNavIcons
import com.dantech.splitvibe.designsystem.components.SvNavItem
import com.dantech.splitvibe.designsystem.components.SvOverflowMenu
import com.dantech.splitvibe.designsystem.components.SvPersonChip
import com.dantech.splitvibe.designsystem.components.SvPersonChoiceRow
import com.dantech.splitvibe.designsystem.components.SvPersonPickRow
import com.dantech.splitvibe.designsystem.components.SvPrimaryButton
import com.dantech.splitvibe.designsystem.components.SvPrimaryFab
import com.dantech.splitvibe.designsystem.components.SvQuietButton
import com.dantech.splitvibe.designsystem.components.SvRadio
import com.dantech.splitvibe.designsystem.components.SvRowPosition
import com.dantech.splitvibe.designsystem.components.SvSectionHeader
import com.dantech.splitvibe.designsystem.components.SvSmallAction
import com.dantech.splitvibe.designsystem.components.SvSplitButton
import com.dantech.splitvibe.designsystem.components.SvStatBar
import com.dantech.splitvibe.designsystem.components.SvSuccessMark
import com.dantech.splitvibe.designsystem.components.SvSwitch
import com.dantech.splitvibe.designsystem.components.SvTag
import com.dantech.splitvibe.designsystem.components.SvTagKind
import com.dantech.splitvibe.designsystem.components.SvTextAction
import com.dantech.splitvibe.designsystem.components.SvTextField
import com.dantech.splitvibe.designsystem.components.SvTicket
import com.dantech.splitvibe.designsystem.components.SvTile
import com.dantech.splitvibe.designsystem.components.SvTileKind
import com.dantech.splitvibe.designsystem.components.SvTopBar
import com.dantech.splitvibe.designsystem.components.SvWorkingRow
import com.dantech.splitvibe.designsystem.motion.SvAmount
import com.dantech.splitvibe.designsystem.tokens.SvElevation
import com.dantech.splitvibe.designsystem.tokens.SvSpacing
import com.dantech.splitvibe.designsystem.tokens.amountLarge
import com.dantech.splitvibe.designsystem.tokens.amountMedium
import com.dantech.splitvibe.designsystem.tokens.amountSmall
import com.dantech.splitvibe.designsystem.tokens.elevation
import com.dantech.splitvibe.designsystem.tokens.surface
import com.dantech.splitvibe.domain.BillCategory
import com.dantech.splitvibe.ui.common.style

/** Every design system component with sample content, in the order of docs/design-system.md. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CatalogScreen() {
    Scaffold(
        topBar = { SvLargeTopBar(title = "Components") },
        bottomBar = {
            SvBottomBar { SvPrimaryButton("SvPrimaryButton in SvBottomBar", onClick = {}) }
        },
        floatingActionButton = { SvPrimaryFab(text = "SvPrimaryFab", icon = Icons.Default.Add, onClick = {}) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                SvSectionHeader("Colour", hint = "The roles a screen may use.")
                FlowRow(
                    modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    val colors = MaterialTheme.colorScheme
                    Swatch("primary", colors.primary, colors.onPrimary)
                    Swatch("primaryContainer", colors.primaryContainer, colors.onPrimaryContainer)
                    Swatch("tertiaryContainer", colors.tertiaryContainer, colors.onTertiaryContainer)
                    Swatch("errorContainer", colors.errorContainer, colors.onErrorContainer)
                    Swatch("surfaceContainer", colors.surfaceContainer, colors.onSurface)
                }
            }

            item {
                SvSectionHeader("Amounts", hint = "SvAmount with the three amount styles.")
                // Wraps, so that the amounts stay apart when large text makes them wider than the screen.
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.xl),
                    itemVerticalAlignment = Alignment.Bottom,
                ) {
                    SvAmount(902_664, format = ::sampleMoney, style = MaterialTheme.typography.amountLarge, countUp = true)
                    SvAmount(400_680, format = ::sampleMoney, style = MaterialTheme.typography.amountMedium)
                    SvAmount(24_000, format = ::sampleMoney, style = MaterialTheme.typography.amountSmall)
                }
            }

            item {
                SvSectionHeader(
                    "Hero",
                    hint = "SvHero in violet, lime and the bar's colour, with SvHeroAmount. A long amount shrinks, " +
                        "then goes on to a second line.",
                )
                HeroSamples()
            }

            item {
                SvSectionHeader(
                    "Tiles and tags",
                    hint = "SvTile in its three colours, SvTag, and SvIconTile with the style of each category.",
                )
                TileSamples()
            }

            item {
                SvSectionHeader("Top bars", hint = "SvLargeTopBar of a main screen, SvTopBar of a screen opened from another.")
                SvLargeTopBar(
                    title = "Requests",
                    windowInsets = WindowInsets(0),
                    actions = { SvOverflowMenu(label = "More for the requests", options = listOf(SvMenuOption("Refresh") {})) },
                )
                SvTopBar(
                    title = "Edit bill",
                    backLabel = "Back",
                    onBack = {},
                    windowInsets = WindowInsets(0),
                    action = { IconButton(onClick = {}) { Icon(Icons.Default.Edit, contentDescription = "Edit the bill") } },
                )
            }

            item {
                SvSectionHeader("Navigation bar", hint = "SvNavBar: four tabs, a count on one of them, and the round button in the middle.")
                NavBarSample()
            }

            item { SvSectionHeader("Elevation", hint = "SvCard at each level.") }
            items(SvElevation.entries) { level ->
                SvCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
                    elevation = level,
                ) {
                    Text(level.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(SvSpacing.lg))
                }
            }

            item { SvSectionHeader("Grouped rows", hint = "SvListRow, with SvAvatar and an amount.") }
            itemsIndexed(SAMPLE_PEOPLE) { index, name ->
                val amount = 250_000L + index * 75_340L
                SvListRow(
                    position = SvRowPosition.of(index, SAMPLE_PEOPLE.size),
                    label = "$name, ${sampleMoney(amount)}",
                    onClickLabel = "Open $name",
                    onClick = {},
                ) {
                    SvAvatar(name)
                    Spacer(Modifier.width(SvSpacing.md))
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(SvSpacing.md))
                    SvAmount(amount, format = ::sampleMoney, style = MaterialTheme.typography.amountMedium)
                }
            }
            item {
                Spacer(Modifier.height(SvSpacing.sm))
                SvListRow(position = SvRowPosition.Single) {
                    Text("A single row", style = MaterialTheme.typography.bodyLarge)
                }
            }

            item {
                SvSectionHeader("Messages", hint = "SvMessageCard on the page, and on a card, where it turns grey.")
                Column(
                    modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    SvMessageCard(text = "A notice, with one action.", isError = false, action = "Act on it")
                    SvMessageCard(text = "Something went wrong.", isError = true)
                    SvCard(modifier = Modifier.fillMaxWidth()) {
                        SvMessageCard(text = "On a card or a sheet.", isError = true, modifier = Modifier.padding(SvSpacing.lg))
                    }
                }
            }

            item {
                SvSectionHeader("Working and done")
                Column(modifier = Modifier.padding(horizontal = SvSpacing.screen)) {
                    SvWorkingRow(status = "SvWorkingRow: reading the bill…", cancelLabel = "Cancel", onCancel = {})
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SvLoadingIndicator(modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(SvSpacing.lg))
                        SvSuccessMark(Icons.Default.Check)
                    }
                    Spacer(Modifier.height(SvSpacing.md))
                    SvPrimaryButton("SvPrimaryButton, disabled", onClick = {}, enabled = false)
                }
            }

            item {
                SvSectionHeader("Buttons", hint = "SvQuietButton on the page and on a card, SvSmallAction and SvTextAction.")
                Column(
                    modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                        SvQuietButton("SvQuietButton", onClick = {}, icon = Icons.Default.Share)
                        SvTextAction("SvTextAction", onClick = {})
                    }
                    SvCard(modifier = Modifier.fillMaxWidth()) {
                        // Wraps, so that the text keeps its width when large text makes the buttons wider.
                        FlowRow(
                            modifier = Modifier.padding(SvSpacing.lg),
                            horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("On a card", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            SvQuietButton("Take back", onClick = {})
                            SvSmallAction("Send", onClick = {}, icon = Icons.AutoMirrored.Filled.Send)
                        }
                    }
                }
            }

            item {
                SvSectionHeader(
                    "Fields",
                    hint = "SvTextField with the marks of Vietnamese capitals, a placeholder, a suffix, a prefix, an icon, " +
                        "a button at its end, an error, a field that is off and one of more lines.",
                )
                FieldSamples()
            }

            item {
                SvSectionHeader(
                    "Switches and ticks",
                    hint = "SvSwitch, SvCheckbox and SvRadio in rows that are one control each, and on their own.",
                )
                ToggleSamples()
            }

            item {
                SvSectionHeader("Bottom bar", hint = "What SvBottomBar holds takes the bar's colours: SvSplitButton, SvWorkingRow.")
                SvBottomBar {
                    SvSplitButton(
                        text = "Request ₫651,671 from 2 people",
                        onClick = {},
                        moreLabel = "Other ways to share the split",
                        options = listOf(SvMenuOption("Finish without asking") {}),
                    )
                }
                SvBottomBar { SvWorkingRow(status = "Reading your bill…", cancelLabel = "Cancel", onCancel = {}) }
            }

            item {
                SvSectionHeader("Bill photo", hint = "SvBillPhoto while its text is read.")
                val resources = LocalContext.current.resources
                val sample = remember {
                    BitmapFactory.decodeResource(
                        resources,
                        R.drawable.sample_bill,
                        BitmapFactory.Options().apply {
                            inSampleSize = 4
                        },
                    ).asImageBitmap()
                }
                SvBillPhoto(
                    photo = sample,
                    contentDescription = "Sample bill",
                    scanning = true,
                    modifier = Modifier
                        .padding(horizontal = SvSpacing.screen)
                        .height(200.dp),
                )
            }

            item {
                SvSectionHeader(
                    "People",
                    hint = "SvAvatarStack, SvPersonChip switched on, off, opened and added, SvPersonPickRow and SvPersonChoiceRow.",
                )
                Row(
                    modifier = Modifier.padding(horizontal = SvSpacing.screen, vertical = SvSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SvAvatarStack(names = SAMPLE_PEOPLE)
                    SvAvatarStack(names = SAMPLE_PEOPLE, size = 22.dp)
                }
                var meIncluded by remember { mutableStateOf(true) }
                FlowRow(
                    modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                ) {
                    SvPersonChip(name = "Me", selected = meIncluded, onClick = { meIncluded = !meIncluded })
                    SvPersonChip(name = SAMPLE_PEOPLE[2], selected = false, onClick = {})
                    SvPersonChip(name = SAMPLE_PEOPLE[0], removeLabel = "Remove ${SAMPLE_PEOPLE[0]}", onRemove = {})
                    SvPersonChip(name = SAMPLE_PEOPLE[1], onClick = {}, onClickLabel = "Change ${SAMPLE_PEOPLE[1]}")
                }
                var picked by remember { mutableStateOf(setOf(SAMPLE_PEOPLE[1])) }
                SAMPLE_PEOPLE.take(2).forEach { name ->
                    SvPersonPickRow(
                        name = name,
                        detail = "@${name.substringAfterLast(' ').lowercase()}",
                        checked = name in picked,
                        onCheckedChange = { picked = if (it) picked + name else picked - name },
                    )
                }
                var payer by remember { mutableStateOf(SAMPLE_PEOPLE[0]) }
                SAMPLE_PEOPLE.take(2).forEach { name ->
                    SvPersonChoiceRow(name = name, selected = name == payer, onSelect = { payer = name })
                }
            }

            item {
                SvSectionHeader("Empty", hint = "SvEmptyState, in place of what is not there yet.")
                SvEmptyState(icon = Icons.Default.Person, text = "Nobody is sharing this bill yet. Add yourself or pick people.")
            }

            item {
                SvSectionHeader("Choices", hint = "SvChoiceGroup, SvChoiceChips, SvSplitButton and SvOverflowMenu.")
                var mode by remember { mutableIntStateOf(0) }
                var way by remember { mutableIntStateOf(1) }
                Column(
                    modifier = Modifier.padding(horizontal = SvSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(SvSpacing.md),
                ) {
                    SvChoiceGroup(options = listOf("Equally", "By item", "By amount"), selected = mode, onSelect = { mode = it })
                    SvChoiceChips(
                        options = listOf("Equally", "By item", "By shares", "By percent", "By amount"),
                        selected = way,
                        onSelect = { way = it },
                    )
                    SvChoiceChips(
                        options = listOf("Equally", "By item", "By shares", "By percent", "By amount"),
                        selected = way,
                        onSelect = { way = it },
                        singleRow = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SvSplitButton(
                            text = "Request all",
                            onClick = {},
                            moreLabel = "More ways to request",
                            options = listOf(SvMenuOption("Request one by one") {}, SvMenuOption("Copy the amounts") {}),
                            modifier = Modifier.weight(1f),
                        )
                        SvOverflowMenu(label = "More for this bill", options = listOf(SvMenuOption("Remove") {}))
                    }
                }
            }

            item {
                SvSectionHeader("Measures", hint = "SvMeter, SvStatBar and SvBarChart, each of which a screen reader reads as words.")
                MeasureSamples()
            }

            item {
                SvSectionHeader("Ticket", hint = "SvTicket, the frame of the card that shows a whole split.")
                TicketSample()
            }

            item {
                SvSectionHeader("Floating", hint = "SvFloatingToolbar and SvFabMenu, closed.")
                var menuOpen by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SvSpacing.screen),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    SvFloatingToolbar {
                        IconButton(onClick = {}) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        IconButton(onClick = {}) { Icon(Icons.Default.Share, contentDescription = "Share") }
                        IconButton(onClick = {}) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                    }
                    SvFabMenu(
                        expanded = menuOpen,
                        onExpandedChange = { menuOpen = it },
                        label = "Start a split",
                        icon = Icons.Default.Add,
                        items = listOf(
                            SvFabMenuItem("Take photo", Icons.Default.Add) {},
                            SvFabMenuItem("Type it in", Icons.Default.Edit) {},
                        ),
                    )
                }
            }

            item {
                SvSectionHeader("Sheet", hint = "SvBottomSheet, opened from a button.")
                var sheetOpen by remember { mutableStateOf(false) }
                TextButton(onClick = { sheetOpen = true }, modifier = Modifier.padding(horizontal = SvSpacing.sm)) { Text("Open a sheet") }
                if (sheetOpen) {
                    SvBottomSheet(
                        title = "A sheet",
                        supportingText = "A short task on top of the screen.",
                        onDismiss = { sheetOpen = false },
                        actions = { SvPrimaryButton(text = "Done", onClick = { sheetOpen = false }) },
                    ) {
                        SvPersonPickRow(name = SAMPLE_PEOPLE[0], checked = true, onCheckedChange = {})
                    }
                }
            }

            item {
                SvSectionHeader("Dialog", hint = "SvDialog, opened from a button: a question, and one for what cannot be undone.")
                DialogSamples()
            }

            item {
                SvSectionHeader("Shadow by hand", hint = "Modifier.elevation and SvElevation.surface, for a shape that is not a card.")
                val level = SvElevation.Level3
                Box(
                    modifier = Modifier
                        .padding(horizontal = SvSpacing.xl, vertical = SvSpacing.sm)
                        .size(64.dp)
                        .elevation(level, CircleShape)
                        .background(level.surface, CircleShape),
                )
                // Room below the last sample, so the floating button never covers it.
                Spacer(Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun NavBarSample() {
    var tab by remember { mutableIntStateOf(0) }
    SvNavBar(
        items = listOf(
            SvNavItem("Home", SvNavIcons.Home),
            SvNavItem("Groups", SvNavIcons.Groups),
            SvNavItem("Requests", SvNavIcons.Requests, count = 2),
            SvNavItem("Insights", SvNavIcons.Insights),
        ),
        selected = tab,
        onSelect = { tab = it },
        centerLabel = "Split a bill",
        centerIcon = SvNavIcons.Split,
        onCenterClick = {},
    )
}

@Composable
private fun HeroSamples() {
    Column(modifier = Modifier.padding(horizontal = SvSpacing.screen), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        SvHero(
            label = {
                Text("Friends owe you", modifier = Modifier.weight(1f))
                SvTag("2 requests", kind = SvTagKind.Accent)
            },
            amount = { SvHeroAmount(prefix = "₫", number = "651,671", suffix = "", spokenText = "651,671 dong") },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                SvAvatarStack(names = SAMPLE_PEOPLE.take(2))
                Text("Minh and Lan")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                SvTag("Nov 13, 2026")
                SvTag("3 people")
            }
            SvQuietButton("You owe ₫150,000 to Dana", onClick = {})
        }
        SvHero(
            kind = SvHeroKind.Accent,
            label = { Text("You get back") },
            amount = { SvHeroAmount(prefix = "THB ", number = "35.43", suffix = "", spokenText = "35.43 baht") },
        ) {
            Text("Balances are in THB, across every bill and payment back.")
        }
        SvHero(kind = SvHeroKind.Bar) {
            Text("Picking is closed. Check the split, then send what everyone owes.", style = MaterialTheme.typography.titleLarge)
            Text("Send this link to everyone at the table.")
            Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                SvPrimaryButton("Share the link", onClick = {}, modifier = Modifier.weight(1f))
                SvQuietButton("Copy", onClick = {})
            }
        }
        SvHero(
            label = { Text("Bill total, written as in Vietnam") },
            amount = { SvHeroAmount(prefix = "", number = "1.902.664", suffix = " ₫", spokenText = "1,902,664 dong") },
        )
        SvHero(
            label = { Text("An amount too long for one line") },
            amount = {
                SvHeroAmount(
                    prefix = "₫",
                    number = "1,234,567,890,123,456,789,012,345,678",
                    suffix = "",
                    spokenText = "1,234,567,890,123,456,789,012,345,678 dong",
                )
            },
        )
    }
}

@Composable
private fun TileSamples() {
    Column(modifier = Modifier.padding(horizontal = SvSpacing.screen), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            SvTile(
                label = "November",
                value = sampleMoney(3_823_038),
                kind = SvTileKind.Accent,
                caption = "Your share of 6 bills",
                trailing = { Icon(Icons.Default.DateRange, contentDescription = null) },
                modifier = Modifier.weight(1f),
                onClick = {},
                onClickLabel = "Open the insights",
            )
            SvTile(
                label = "Chiang Mai trip",
                value = "THB 35.43",
                kind = SvTileKind.Ink,
                note = "You get back",
                leading = { SvAvatar("Chiang Mai trip", size = 28.dp) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
            SvTile(
                label = "You owe",
                value = sampleMoney(150_000),
                kind = SvTileKind.Ink,
                modifier = Modifier.weight(1f),
            )
            SvTile(
                label = "Friends owe you",
                value = sampleMoney(651_671),
                kind = SvTileKind.Brand,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            SvTag("Draft")
            SvTag("Owner", kind = SvTagKind.Strong)
            SvTag("Paid and confirmed", kind = SvTagKind.Accent, icon = Icons.Default.Check)
        }
        SvCard(modifier = Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier.padding(SvSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
            ) {
                SvTag("Paused")
                SvTag("Owner", kind = SvTagKind.Strong)
                SvTag("Pro plan", kind = SvTagKind.Accent)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            BillCategory.entries.forEach { category ->
                val style = category.style
                SvIconTile(style.icon, style.color)
            }
        }
    }
}

@Composable
private fun FieldSamples() {
    var where by remember { mutableStateOf("Ẳ QUÁN ỐC BA MIỂN") }
    var date by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("902664") }
    var handle by remember { mutableStateOf("minhtran") }
    var search by remember { mutableStateOf("") }
    var change by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("Vietcombank\nAccount 0123 4567 89") }
    Column(modifier = Modifier.padding(horizontal = SvSpacing.screen), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        SvTextField(
            where,
            { where = it },
            label = "Where",
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        SvTextField(date, { date = it }, label = "Date", modifier = Modifier.fillMaxWidth(), placeholder = "2026-11-13")
        // Across the full width, as the bill editor lays out its total, so that large text still shows the whole amount.
        SvTextField(
            total,
            { total = it },
            label = "Total",
            modifier = Modifier.fillMaxWidth(),
            suffix = "VND",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        )
        SvTextField(
            handle,
            { handle = it },
            label = "PayPal.Me link",
            modifier = Modifier.fillMaxWidth(),
            prefix = "paypal.me/",
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        )
        SvTextField(
            search,
            { search = it },
            label = "Search",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Find a bill",
            leadingIcon = Icons.Default.Search,
        )
        SvTextField(
            change,
            { change = it },
            label = "Change the split",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "Tell the AI what to change",
            trailing = {
                IconButton(onClick = {}) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask for the change") }
            },
        )
        SvTextField(
            "VNX",
            {},
            label = "Local currency",
            modifier = Modifier.fillMaxWidth(),
            isError = true,
            supportingText = "Type a code of three letters, such as VND.",
        )
        SvTextField("Pho 24", {}, label = "Where, while the bill is read", modifier = Modifier.fillMaxWidth(), enabled = false)
        SvTextField(
            details,
            { details = it },
            label = "Details",
            modifier = Modifier.fillMaxWidth(),
            supportingText = "The bank and the account, as the person paying needs them.",
            singleLine = false,
        )
    }
}

@Composable
private fun ToggleSamples() {
    var speech by remember { mutableStateOf(true) }
    var phoneData by remember { mutableStateOf(false) }
    var plan by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.padding(horizontal = SvSpacing.screen), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        SvCard(modifier = Modifier.fillMaxWidth()) {
            ToggleRow(Modifier.toggleable(value = speech, role = Role.Switch, onValueChange = { speech = it })) {
                Text("Use the phone's online speech service", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                SvSwitch(checked = speech, onCheckedChange = null)
            }
            ToggleRow(Modifier.toggleable(value = phoneData, role = Role.Checkbox, onValueChange = { phoneData = it })) {
                SvCheckbox(checked = phoneData, onCheckedChange = null)
                Text("Also delete the bills on this phone", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
            listOf("Monthly", "Yearly").forEachIndexed { index, name ->
                ToggleRow(Modifier.selectable(selected = plan == index, role = Role.RadioButton, onClick = { plan = index })) {
                    SvRadio(selected = plan == index, onClick = null)
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
            SvSwitch(checked = false, onCheckedChange = {})
            SvSwitch(checked = true, onCheckedChange = {}, enabled = false)
            SvCheckbox(checked = true, onCheckedChange = {}, enabled = false)
            SvRadio(selected = false, onClick = {})
        }
    }
}

@Composable
private fun ToggleRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = SvSpacing.lg, vertical = SvSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
        content = content,
    )
}

@Composable
private fun MeasureSamples() {
    Column(modifier = Modifier.padding(horizontal = SvSpacing.screen), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
        SvCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.md)) {
                Text("9 of 10 calls left this month.", style = MaterialTheme.typography.bodyLargeEmphasized)
                SvMeter(value = 9f, max = 10f, description = "9 of 10 calls left")
                SvMeter(value = 2.8f, max = 3f, description = "2.8 of 3 free calls left")
                SvMeter(value = 120f, max = 300f, description = "120 of 300 calls left")
            }
        }
        SvCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(SvSpacing.lg), verticalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                listOf(
                    BillCategory.RENT to 1f,
                    BillCategory.UTILITIES to 0.067f,
                    BillCategory.SHOPPING to 0.028f,
                ).forEach { (category, share) ->
                    Text(category.label, style = MaterialTheme.typography.bodyMediumEmphasized)
                    SvStatBar(fraction = share, description = null, color = category.style.color)
                }
                Text("Lan owes", style = MaterialTheme.typography.bodyMediumEmphasized)
                SvStatBar(fraction = 0.84f, description = "Lan owes 84% of the most anyone owes", fromEnd = true)
            }
        }
        SvCard(modifier = Modifier.fillMaxWidth()) {
            SvBarChart(
                bars = SAMPLE_MONTHS.mapIndexed { index, month -> SvChartBar(month, SAMPLE_SPENDING[index]) },
                description = "Spending by month from December 2025 to November 2026. November 2026 is the highest, then October 2026.",
                modifier = Modifier.padding(SvSpacing.lg),
                highlighted = SAMPLE_MONTHS.lastIndex,
            )
        }
    }
}

@Composable
private fun TicketSample() {
    SvTicket(
        modifier = Modifier.padding(horizontal = SvSpacing.screen),
        stub = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm)) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Split with Split Vibe", style = MaterialTheme.typography.bodySmallEmphasized)
            }
        },
    ) {
        Text("QUÁN ỐC BA MIỀN · Nov 13, 2026", style = MaterialTheme.typography.bodySmallEmphasized)
        Text(sampleMoney(902_664), style = MaterialTheme.typography.amountLarge)
        SAMPLE_PEOPLE.forEachIndexed { index, name ->
            Row(
                modifier = Modifier.heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
            ) {
                SvAvatar(name, size = 28.dp)
                Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMediumEmphasized)
                Text(sampleMoney(250_993L + index * 75_000L), style = MaterialTheme.typography.amountSmall)
            }
        }
    }
}

@Composable
private fun DialogSamples() {
    var open by remember { mutableStateOf<String?>(null) }
    FlowRow(
        modifier = Modifier.padding(horizontal = SvSpacing.screen),
        horizontalArrangement = Arrangement.spacedBy(SvSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(SvSpacing.sm),
    ) {
        SvQuietButton("Open a dialog", onClick = { open = "question" })
        SvQuietButton("Open one that cannot be undone", onClick = { open = "irreversible" })
    }
    when (open) {
        "question" -> SvDialog(
            title = "Leave the group?",
            text = "Your bills stay in the group, and you can join again with a new invite.",
            confirmLabel = "Leave",
            onConfirm = { open = null },
            dismissLabel = "Cancel",
            onDismiss = { open = null },
        )

        "irreversible" -> {
            var withPhoneData by remember { mutableStateOf(false) }
            SvDialog(
                title = "Delete your account?",
                text = "The server deletes your account, with its plan and its bill links.",
                confirmLabel = "Delete",
                onConfirm = { open = null },
                dismissLabel = "Cancel",
                onDismiss = { open = null },
                irreversible = true,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = withPhoneData, role = Role.Checkbox, onValueChange = { withPhoneData = it })
                        .heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SvSpacing.md),
                ) {
                    SvCheckbox(checked = withPhoneData, onCheckedChange = null)
                    Text("Also delete the bills, people and payment methods on this phone", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color, onColor: Color) {
    Box(
        modifier = Modifier
            .background(color, MaterialTheme.shapes.medium)
            .padding(horizontal = SvSpacing.md, vertical = SvSpacing.sm),
    ) {
        Text(name, color = onColor, style = MaterialTheme.typography.labelLarge)
    }
}

private fun sampleMoney(minor: Long): String = "₫%,d".format(minor)

private val SAMPLE_PEOPLE = listOf("Trần Quang Minh", "Lê Thị Lan", "Emma Wilson")

private val SAMPLE_MONTHS = listOf("D", "J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N")
private val SAMPLE_SPENDING = listOf(800_000f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 9_762_024f, 11_749_409f)
