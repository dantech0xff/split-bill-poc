# Design system

How Split Vibe looks, and the rules that keep it looking that way. Everything described here lives
in the `:core:designsystem` module, under
`core/designsystem/src/main/java/com/dantech/splitvibe/designsystem/`: the theme in `theme/`, the
tokens in `tokens/`, the components in `components/` and the pieces of motion in `motion/`. The
reference images in `app/src/test/screenshots` show the result on every screen, sheet and dialog,
many of them in the dark theme too; [testing.md](testing.md#screenshot-tests) says how they are
compared and recorded. The
look follows the boards the redesign was approved from, which are kept with its plan in
[`plans/261120-0528-ui-redesign/boards/`](../plans/261120-0528-ui-redesign/boards/README.md).

## The one rule

Screens decide nothing about the look themselves. They use the components and tokens below and
never set a theme, a colour value, a font family, a shadow, an animation, an animation timing or a
delay of their own, and they never opt in to an experimental Expressive API. Nor do they call
Material's own text field, tick box, radio button, switch, dialog or tonal button: the design system
draws those in the app's look as `SvTextField`, `SvCheckbox`, `SvRadio`, `SvSwitch`, `SvDialog` and
`SvQuietButton`. `DesignSystemBoundaryTest` reads every source file in `app/src/main/java` and
`app/src/debug/java`, fails when one of them does any of this, and names the file and line.

The reason is practical. The app uses a release candidate of Material 3 Expressive, so when the
library changes, only one module has to follow; and what is decided in one place looks the same on
every screen.

## Principles

1. **One main action per screen.** It is the large pill, usually in the floating bar at the bottom.
   On the four main screens, the round button in the middle of the navigation bar starts a split.
2. **Amounts are the hero.** They use the amount text styles, in Bricolage Grotesque with digits of
   equal width, and stand at the end of their row so that a column of amounts lines up. The one
   amount a screen is about sits large in the block at its top.
3. **Group by space and surface, not by lines.** Related rows sit together on one card, white in the
   light theme. There are no divider lines.
4. **Two loud colours, each with a job.** Violet fills the block a screen is about and is the main
   action on the light page. Lime is the accent: ticks, tags, good news, and the main action wherever
   violet would not show, which is in the dark theme, on the floating bar and on a violet block.
   Everything else is ink on neutral grounds, and the other colours carry meaning: a person, a
   category, a notice, money coming back or owed, an error.
5. **Depth is elevation.** In the light theme cards lie flat on the page, and only what floats over
   the content casts a shadow. A thing that floats gets an elevation level, never a shadow of its
   own.

## Theme

`SplitVibeTheme` wraps Material 3 Expressive with the app's colour scheme, type scale and shapes and
Material's "expressive" motion scheme, and provides `SvPalette` and the system's animation setting
(`LocalReducedMotion`) with them. `MainActivity` applies it once, and so does the activity of the
debug catalog. It follows the system's light or dark setting; pass `darkTheme` to force one, which
the tests do.

The window behind the app is the page too. `Theme.SplitVibe` in `app/src/main/res/values/themes.xml`
and `values-night/themes.xml` takes `window_light` and `window_dark` from
`app/src/main/res/values/colors.xml`, so that a cold start, the splash screen of Android 12 and later
and the list of recent apps show the page rather than the platform's own grey. A change of the
page's colour has to be made there as well: `WindowBackgroundTest` fails until it is.

## Colour

Neutral grounds, vivid violet blocks and a lime accent, with ink text. Every raw colour is declared
once, as a named colour of `Swatch` in `tokens/SvColors.kt`. Material's colour roles and `SvPalette`
both point at those colours, so changing a value leaves no stale copy behind. Screens never read the
raw colours, only the roles and the palette.

### Material's roles

| Role | Light | Dark | Used for |
|---|---|---|---|
| `primary` / `onPrimary` | `#5B34F5` / `#FFFFFF` | `#C9F73A` / `#111114` | The main action on the page and on a card, and what is filled, switched on or highlighted there |
| `surface`, `background` / `onSurface` | `#F6F6F4` / `#111114` | `#0F0F12` / `#F4F4F1` | The page, and the text on it |
| `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest` | `#FFFFFF`, `#FAFAF8`, `#F0EFEA`, `#E9E8E2`, `#E1E0D9` | `#0A0A0C`, `#1C1C21`, `#232329`, `#2A2A31`, `#33333B` | Cards, sheets, dialogs and quiet controls, through the elevation levels and the grounds below |
| `onSurfaceVariant` | `#5F5E66` | `#B7B6BF` | Secondary text |
| `outline` / `outlineVariant` | `#76757E` / `#D9D8D2` | `#8E8D97` / `#3A3A42` | The box of a field; the handle of a sheet, the outline of a menu and the empty part of a meter on the page |
| `inverseSurface` / `inverseOnSurface` | `#111114` / `#F4F4F1` | `#F4F4F1` / `#111114` | What is chosen, ticked or switched on, and the account button on the home: ink in the light theme, paper in the dark one |
| `error` / `onError` | `#B42A14` / `#FFFFFF` | `#FF8A70` / `#111114` | Errors, and the button that goes ahead with something that cannot be undone |
| `secondaryContainer` / `onSecondaryContainer` | `#E9E8E2` / `#111114` | `#33333B` / `#F4F4F1` | Material's own tonal buttons and badges |

`primary` is violet in the light theme and lime in the dark one, while the violet blocks stay violet
in both. That is why the brand colour lives in `SvPalette` and not in `primary`.
`secondaryContainer` is a light neutral rather than ink, so that anything of Material's drawn on it
stays lighter than the main action.

The other roles, such as `secondary`, `tertiary`, the containers of `primary`, `tertiary` and
`error`, `surfaceVariant`, `surfaceDim`, `surfaceBright` and the fixed roles, are seldom read by the
app. They take values of the same family, so that no Material component strays from the look: the
dim and bright surfaces are the dimmest and brightest grounds of each theme, and the fixed roles are
the same in both themes, as Material defines them. Their values are in `tokens/SvColors.kt`.

### The palette of Split Vibe

`SvPalette` (`tokens/SvPalette.kt`) holds the colours that Material's roles have no place for.
Screens read them with `SvTheme.palette`.

| Token | Light | Dark | Used for |
|---|---|---|---|
| `brand` / `onBrand` | `#5B34F5` / `#FFFFFF` | the same | The violet blocks: the top of a screen, the ticket of a split |
| `accent` / `onAccent` | `#C9F73A` / `#111114` | the same | Lime blocks, tags and ticks, and the main action on the floating bar and on a violet block |
| `bar` / `onBar` / `onBarQuiet` | `#111114` / `#FFFFFF` / `#C6C5CE` | `#26262C` / `#F4F4F1` / `#B7B6BF` | The floating bars, their text, and their quieter text, such as a tab that is not selected |
| `barOutline` | `#111114` | `#3A3A42` | The thin edge a floating bar draws around itself, which shows only in the dark theme |
| `moneyIn` / `moneyOut` | `#0A6B4E` / `#A83508` | `#5BE3B0` / `#FF9B5E` | The text of money that comes back to someone ("gets back") and of money someone owes ("owes") |
| `moneyInBar` | `#17B383` | `#17B383` | The bar that draws money coming back, such as on the balances of a group |
| `person` / `onPerson` | `#FF6FB5`, `#FF8A3D`, `#5CC8FF`, `#FFD23F`, `#5BE3A0`, `#B9A2FF`, `#7CE0D6` / `#111114` | the same | The tile behind a person's initials, picked from the name, so the same person always has the same colour. `onPerson` is the ink on every bright colour: people, categories and the notice |
| `category` | `#FF8A3D`, `#5BE3A0`, `#5CC8FF`, `#B9A2FF`, `#FF6FB5`, `#FFD23F`, `#7CE0D6`, `#A8B4FF`, `#D9D8D2` | the same | The tile of a bill's category, in the order `BillCategory` declares them: food and drink, groceries, transport, lodging, entertainment, shopping, utilities, rent, other |
| `notice` | `#FFD23F` | the same | The tile behind the icon of a notice |
| `photoZone` | `#E7E6E0` | `#2A2A31` | The tray a bill's photo lies on |
| `radioRing` | `#8A8992` | `#8E8D97` | The ring of a choice of one that is not chosen |
| `blankTile` | `#D9D8D2` | `#3A3A42` | The tile of something that stands for none of a kind, such as the bills in no group |

The app gives each bill category its icon and its colour from this palette in
`app/src/main/java/com/dantech/splitvibe/ui/common/CategoryStyle.kt`.

### The mark and the launcher icon

The app's mark is a circle split in two, a white half up and to the left and a lime half down and to
the right, on violet: `onBrand` and `accent` on `brand`. `SvBrandTopBar` draws it on a violet tile at
the top of Home, and `SvBrandMark` draws it without the tile on the stub of a shared ticket. The
launcher icon (`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`) draws the same mark larger, with
the halves further apart, so that the split reads on a home screen. Android 12 and later show it on
the splash screen too, and its monochrome layer gives the themed icon of Android 13 and later. The
small icon of a notice is the same mark in one colour. `LauncherIconTest` checks that the icon keeps
the palette's colours and draws `store-icon-512.png`, the picture of the icon for the Play listing.

### Readable text

`ColorTokensTest` checks that text has a contrast of at least 4.5:1 on whatever the palette pairs it
with: each role's text on its role; `onSurface`, `onSurfaceVariant`, `primary` and `error` on the
page and on every container; the text of `SvPalette` on its blocks, bars and tiles; and `moneyIn` and
`moneyOut` on the page and on every container. Outlines and `radioRing` have to reach 3:1. Change a
value and the test tells you whether text is still readable. It leaves out two tokens on purpose:
`barOutline` is a decorative edge, and the bar is recognisable without it by its own colour, text and
icons; `moneyInBar` only draws an amount that the words beside it already say, in `moneyIn`.

## Type

Two typefaces ship with the app, so text looks the same on every phone, and both cover Vietnamese
in full. They form three font families in `tokens/SvTypography.kt`:

| Family | Files | Sets |
|---|---|---|
| Display | Bricolage Grotesque Bold and ExtraBold, the cut drawn for 14pt | The titles `displayMedium`, `displaySmall`, `headline…` and `titleLarge`, and the amount styles but `amountHero` |
| Display hero | Bricolage Grotesque ExtraBold, the narrower cut drawn for 96pt | `displayLarge` and `amountHero` |
| Text | Be Vietnam Pro Regular, Medium, SemiBold and Bold | Everything else: small titles, body text and labels |

The display hero cut is a family of its own because Compose picks a file within a family by weight
and style, never by size, and it has the same weight as the ExtraBold of the display family.

Every one of Material's 15 styles and its emphasized version is set in one of the three families.
The titles are ExtraBold, so an emphasized title cannot be heavier and equals its plain style. In
the text family, body text is Regular and emphasized SemiBold, and small titles and labels are
SemiBold and emphasized Bold. Large titles are set a little tighter than small ones. The sizes are
in `tokens/SvTypography.kt`.

Four more styles are for amounts, read from `MaterialTheme.typography`. They are set in Bricolage
Grotesque with digits of equal width (`tnum`), so that a column of amounts lines up and a changing
number does not jump; Be Vietnam Pro has no digits of equal width, so amounts never use it. They are
the same whatever `Typography` they are read from.

| Style | Use |
|---|---|
| `amountHero` | The one amount a whole screen is built around, in its top block, such as what friends owe you |
| `amountLarge` | The one amount a screen or a card is about |
| `amountMedium` | An amount a list row is about, such as what a person pays |
| `amountSmall` | An amount that supports something else, such as the price of one item |

`TypographyTest` checks that every style draws in a bundled family, the titles and amounts in
Bricolage Grotesque and the rest in Be Vietnam Pro, that only `displayLarge` and `amountHero` use the
hero cut, and that every amount style has digits of equal width. `FontLoadingTest`, in the app's
unit tests, draws text with each of the seven font files and with the phone's default font, and
fails when the two come out the same width, which is how a font that did not load shows.

### Where the fonts come from

The seven font files are in `core/designsystem/src/main/res/font/`, and the licence of each typeface,
the SIL Open Font License, is in `core/designsystem/src/main/res/raw/`. Each file was downloaded from
its repository at a pinned commit:

| Typeface | Repository | Commit | Path in the repository |
|---|---|---|---|
| Bricolage Grotesque | [`ateliertriay/bricolage`](https://github.com/ateliertriay/bricolage) | `84745e5b96261ae5f8c6c856e262fe78d1d6efdd` | `fonts/ttf/` (`BricolageGrotesque-Bold.ttf`, `BricolageGrotesque-ExtraBold.ttf`, `BricolageGrotesque96pt-ExtraBold.ttf`) and `OFL.txt` |
| Be Vietnam Pro | [`google/fonts`](https://github.com/google/fonts) | `cc5661d43cd4f9aad5567033a8e15ca28dc35c86` | `ofl/bevietnampro/` (`BeVietnamPro-Regular.ttf`, `-Medium`, `-SemiBold`, `-Bold`) and `OFL.txt` |

The files are not dependencies, so Gradle's dependency verification never sees them. Their SHA-256
checksums are in `core/designsystem/font-checksums.sha256`, which is the one place that records them.
`FontChecksumsTest` checks the files in the module against that list, and that the list names every
font and licence there. The `fonts` job of `.github/workflows/checksums.yml` downloads each file
again from its repository at the pinned commit, on a clean machine, and compares it with the list
too. To replace a font, change the file, the list and the workflow's addresses together.

The licences ship with the app because the SIL Open Font License asks for that. No code reads them,
so `core/designsystem/src/main/res/raw/fonts_keep.xml` keeps resource shrinking from removing them
from the release build. The app has no screen of font licences: the licence asks only that its text
go with the fonts.

## Spacing

Distances come from `SvSpacing`, a 4dp grid: `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24, `xxl` 32.
`screen`, 20dp, is the margin between the content and the left and right edges of the screen.

## Shape

Shapes come from `MaterialTheme.shapes`, Material's scale with rounder corners: `extraSmall` 8dp,
`small` 12, `medium` 16, `large` 24 and `extraLarge` 32. The three steps that Material's expressive
components add are set too, so that each step stays rounder than the one before it:
`largeIncreased` 28dp, `extraLargeIncreased` 36 and `extraExtraLarge` 48.

`SvShapes` adds what the scale does not have: `pill`, with fully rounded ends, for buttons, tags and
the floating bars; `tile`, 14dp, for a small square tile such as the one behind a category's icon;
and the corners of a group of rows, 24dp on the outside of the group and none where two of its rows
meet, so that the rows of a group form one card.

## Elevation

`SvElevation` has four levels. In the light theme cards lie flat on the page, told apart by their
white against the grey ground, and only what floats over the content casts a shadow, a soft shadow
in the colour of ink below it. In the dark theme shadows cannot be seen, so a level is a lighter
surface instead and nothing is drawn around it.

| Level | Light theme | Dark theme | Use |
|---|---|---|---|
| `Level1` | White, flat | `surfaceContainerLow` | Rests on the page: a card |
| `Level2` | White, flat | `surfaceContainer` | A card that can be pressed |
| `Level3` | White, with a shadow | `surfaceContainerHigh` | Floats over the content: the floating bars, a floating button |
| `Level4` | White, with a deeper shadow | `surfaceContainerHighest` | On top of everything: an item being dragged |

Use `Modifier.elevation(level, shape)` before the background of the same shape, and
`level.surface` for the colour. The components below already do both.

A Material component that draws a shadow of its own gets it switched off and takes a level
instead, so every shadow in the app looks alike. Where Material offers no way to switch it off,
as for the button of the floating menu, the design system draws that part itself. A menu opens in
a window of its own, where no shadow can be drawn around it, so it has an outline instead.

A surface that grows into another screen rests flat while it moves, because its shadow would have
to be blurred again at every size, and fades its shadow back in once it is in place.
`SvPrimaryFab` does this.

Only the strength of a shadow may be animated, through the `strength` parameter. It changes
opacity and offset, which are cheap to redraw every frame. The blur radius is fixed per level
because redrawing it every frame is expensive.

## Where a component sits

A component takes its colours from what it lies on, so that a screen never has to choose them. The
surfaces of the design system say what they are: `SvCard`, the rows of a group, a notice, a sheet
and a dialog are a card; `SvBottomBar` and `SvNavBar` are the floating bar; `SvHero`, `SvTile` and
`SvTicket` are the ground of their own colour, a violet or a lime block, or the floating bar for a
dark hero or an ink tile (an ink tile in the dark theme is a card); and `SvInset` is the page
again, set into a card. Everything else lies on the page. The rule is `SvGround`, in
`components/SvCard.kt`.

| What it lies on | The main action (`SvPrimaryButton`, `SvSplitButton`) | A quiet control (`SvQuietButton` and the like) | A small action (`SvSmallAction`) |
|---|---|---|---|
| The page, a card, a sheet or a dialog | `primary`: violet in the light theme, lime in the dark one | White on the light page and light grey on a light card; the next lighter grounds in the dark theme | Ink with lime text in the light theme, lime with ink text in the dark one |
| The floating bar | Lime with ink text | A lighter shade of the bar | Lime with ink text |
| A violet block | Lime with ink text | A darker shade of the block | Lime with ink text |
| A lime block | Ink with lime text | A darker shade of the block | Ink with lime text |

The text on a quiet control is the text colour of its ground. A main action that cannot be used yet
steps back to the quiet shade of what it lies on. What is chosen or ticked is the inverse of the
page: the chosen pill of `SvChoiceGroup`, `SvTabs` and `SvChoiceChips`, a ticked box and a switch
that is on are ink in the light theme and paper in the dark one. A chosen `SvRadio` is a lime disc
with a dark tick in both themes.

A field (`SvTextField`) keeps its label and its supporting text outside its box, in the text colours
of the page or a card, so it belongs only on the page, a card, a sheet or a dialog, never on the
floating bar or on a coloured block.

## Motion

Movement has to explain something: where a thing came from, or that a tap was received. Screens
never write an animation themselves; they use these pieces, which take their timing from `SvMotion`
and from the theme's motion scheme.

| File | Pieces | What they do |
|---|---|---|
| `Bounce.kt` | `Modifier.bounceOnChange`, `Modifier.popIn` | A quick springy bounce each time a value changes, such as a chip switched on or off; nothing moves when the element first appears. `popIn` gives the same bounce to an element added to a screen that is already shown, such as a person added to a split; what is there when the screen appears does not pop. |
| `ReducedMotion.kt` | `LocalReducedMotion` | Whether animations are switched off in the system settings, which `SplitVibeTheme` provides. |
| `RowMotion.kt` | `rowMotion` | A row of a lazy list fades in when it is added, fades out when it is removed, and slides to its new place when the rows above it change. The row needs a key of its own. |
| `SharedSurface.kt` | `Modifier.sharedSurface`, `Modifier.keepSizeDuringTransition`, `SvSharedTransition`, `LocalSvSharedTransition`, `SvSharedSurfaceName` | A surface that grows into the surface with the same name on the next screen, and shrinks back on the way back: the button in the middle of the navigation bar and the split screen are one such pair. A small surface passes `resize`, so that it is laid out again at every size, and `keepSizeDuringTransition` keeps a label at its own size while the surface around it changes shape, so the edge cuts it off instead of it wrapping onto more lines. Within the app's screens the surface also carries its name as `SvSharedSurfaceName`, by which tests find it on both screens; a screen reader never sees it. |
| `StaggeredEntry.kt` | `Modifier.staggeredEntry`, `rememberSvEntryState`, `SvEntryState` | The rows of a screen slide up and fade in one after another when the screen appears. Rows that scroll into view later simply appear. |
| `SvAmount.kt` | `SvAmount` | An amount that runs through the numbers in between when its value changes, and can count up from zero when it first appears. A screen reader always gets the real amount. |
| `SvMotion.kt` | `SvMotion` | The timing of everything that happens while one screen or step turns into another, and how a step of a flow fades out and the next one in. |
| `SvScreenLayer.kt` | `SvScreenLayer` | The layer that shows one screen after another, the navigation's screens and the steps of a flow alike, on the colour of the page, so that between two screens the page shows and never the window behind the app. A shared surface grows from one screen into the next within it. |
| `SvSteps.kt` | `SvSteps` | One step of a flow at a time: the step on screen fades out, then the next fades in. A shared surface in one step turns into the one with the same name in the next: the button that sends the requests shrinks into the round mark that confirms them. Each step has a fixed name, because the name is part of the key that brings back what was saved inside the step after the system ends the app. |

Some components move too: `SvWorkingRow` fades from one status line to the next, `SvSuccessMark`
pops its icon in once with one short confirming vibration, `SvBillPhoto` settles a new photo into its
tray and runs a lime line over it while its text is read, and `SvLoadingIndicator` keeps changing
shape while work runs.

A change of screen has a fixed length instead of a spring. A back swipe scrubs through the change
with the finger, and only parts of equal length stay together and follow the finger evenly; a
spring would be over after the first part of the swipe and leave nothing to preview.

Some people switch animations off in the system settings. Compose then ends ordinary animations at
once by itself. Effects that never end and delays written by hand cannot be known to it, so they
ask `LocalReducedMotion` and stand still or are skipped: the loading indicator stands still, the
line over the photo is not drawn, bounces are skipped, and an amount shows its value at once.
What happens only the first time, such as counting up or popping in, is saved, so a row that
scrolls out of view and back does not do it again. `ReducedMotionTest` checks that nothing
moves on its own in that case and that a bill can still be split from start to finish.

## Components

Every component is in `components/`, one file for a component or for a few that belong together.

| File | Components | Use |
|---|---|---|
| `SvActionIcons.kt` | `SvActionIcons` | The icons of what can be done with a bill, such as scanning, sharing or repeating it, and the spark that marks the AI, as the approved boards draw them. |
| `SvActionTile.kt` | `SvActionTile` | One of a few ways to do the same thing, as tiles of equal width side by side, such as the ways to add the photo of a bill. The usual way stands out. |
| `SvAvatar.kt` | `SvAvatar`, `SvAvatarStack` | A person's initials on a tile of that person's colour, and the people of something as tiles that overlap. Decorative: always say the names in text beside them. |
| `SvBarChart.kt` | `SvBarChart`, `SvChartBar` | Values side by side as columns, such as what was spent each month, with one bar highlighted. It takes a description in words for a screen reader. |
| `SvBesideOrBelow.kt` | `SvBesideOrBelow` | Two parts of one line side by side while they fit, and one below the other when they do not, as with large text, so that no word of either is broken in the middle: a label and its value, the text of a card and its buttons, a large title and the actions beside it. |
| `SvBillPhoto.kt` | `SvBillPhoto`, `SvBillPhotoPlaceholder` | The photo of a bill on its tray, and the tray before a photo is picked. Pass `scanning` while the photo's text is read. |
| `SvBottomSheet.kt` | `SvBottomSheet`, `SvPersonPickRow`, `SvPersonChoiceRow` | A sheet from the bottom for a short task, with a title, the content and the actions at the bottom; it follows the finger and previews the screen behind on a back swipe. A person who can be ticked in a list reads as one tick box, and a person chosen as the one out of a list, such as who paid, as one radio button. |
| `SvButtons.kt` | `SvQuietButton`, `SvSmallAction`, `SvFieldButton`, `SvLinkButton`, `SvTextAction` | The actions besides the main one: a quiet pill for a secondary action; a small ink or lime action that belongs to one row, also as a round button with only an icon; a square action beside a field, such as removing its line; a quiet bar that leads on to what it names; and a word with a touch target of its own, underlined, or red for the action at the end of a screen that deletes something for good. |
| `SvCard.kt` | `SvCard`, `SvInset` | A card that lies flat on the page, in the colour of its level or in a colour that carries meaning, outlined for the one card that waits for an answer; and a panel set into a card in the colour of the page. The grounds that decide the colours of what lies on them are here too. |
| `SvChoiceGroup.kt` | `SvChoiceGroup`, `SvTabs`, `SvChoiceChips` | One option out of a few as pills of equal width, read as radio buttons; the tabs of a screen that shows one of a few lists in the same place, read as tabs; and options that wrap onto more lines, or stay on one line that scrolls sideways. |
| `SvDialog.kt` | `SvDialog` | A question that interrupts the screen until it is answered, with an optional tick box or field and one or two buttons. Pass `irreversible` when going ahead cannot be undone, which makes that button red. |
| `SvEmptyState.kt` | `SvEmptyState` | Takes the place of something that is not there yet, with a soft round icon and a sentence. |
| `SvFabMenu.kt` | `SvFabMenu`, `SvFabMenuItem` | The main action as a floating button that opens a short menu, for a main action that can start in a few ways. The screen keeps whether it is open and closes it on back. |
| `SvFields.kt` | `SvTextField`, `SvFieldRow`, `SvFieldWidth`, `SvSwitch`, `SvCheckbox`, `SvRadio`, `SV_TEXT_FIELD_MAX_LENGTH` | Fields, and the controls of a row that is toggled or chosen as a whole; see [Fields](#fields). |
| `SvFloatingToolbar.kt` | `SvFloatingToolbar` | A short row of actions of equal weight that floats over the content. |
| `SvHero.kt` | `SvHero`, `SvHeroKind`, `SvHeroAmount` | The large block at the top of a screen that carries what the screen is about, violet, lime or dark, and the amount in it. The amount comes in three parts, the symbol before or after the number as the phone's region writes it, shrinks from 56sp down to 20sp to stay on one line, and only then goes on to a second line. |
| `SvListRow.kt` | `SvListRow`, `SvOpenListRow`, `SvRowPosition` | The rows of a group, which together form one card with round corners only at its outside; a row can be tapped or switched, and carries its whole label itself. An open row holds the fields of what is being changed. |
| `SvLoadingIndicator.kt` | `SvLoadingIndicator` | Work of unknown length is running. Stands still when animations are off. |
| `SvMessageCard.kt` | `SvMessageCard` | A notice, or with `isError` an error, inside the page, with an optional action. |
| `SvMeter.kt` | `SvMeter`, `SvStatBar` | How much of an allowance is left, as a row of segments, such as the AI calls left; and one value's part of the largest of its kind, as a thin bar that can also fill from the end, for money owed. Each takes a description in words for a screen reader. |
| `SvNavBar.kt` | `SvNavBar`, `SvNavItem`, `SvNavIcons` | The floating bar of the main screens: four tabs and a round lime button in the middle that starts the main task. A screen reader reads each item as a tab with its count, such as "Requests, 2". When a label does not fit on one line every item shows its icon only, with a lime mark under the selected one. |
| `SvPrimaryButton.kt` | `SvPrimaryButton`, `SvBottomBar` | The main action as a large pill across the full width, or a compact one for the main action of a block; and the floating bar at the bottom of a screen that holds it. |
| `SvPrimaryFab.kt` | `SvPrimaryFab` | The main action as a floating button, for a screen that is mostly a list. |
| `SvSectionHeader.kt` | `SvSectionHeader` | The title above a group, with an optional hint and an optional action at the end of its line, such as "See all". A screen reader can jump from one title to the next. |
| `SvSplitButton.kt` | `SvSplitButton`, `SvOverflowMenu`, `SvMenuOption` | A main action with a few close alternatives behind an arrow, and the ⋮ button of a row or a bar that opens a menu. |
| `SvSuccessMark.kt` | `SvSuccessMark`, `SvAiMark` | The round lime badge that marks that something has just succeeded, which a round button can turn into; and the mark of what the AI does. Both are decorative: say in text what they mark. |
| `SvTallyLine.kt` | `SvTallyLine` | The line below the parts of something that says whether they add up, with a tick when they do and in the colour of errors when they do not. |
| `SvTicket.kt` | `SvTicket` | The frame of a ticket, for the card that shows a whole split at once, such as the one shared as a picture. |
| `SvTile.kt` | `SvTile`, `SvTileKind`, `SvTag`, `SvTagKind`, `SvIconTile`, `SvQuietIconTile`, `SvCategoryIcons`, `SvTileIcons` | A small block that gives one figure at a glance, usually two side by side below the top of a screen; a short label in a small pill, such as "Draft"; an icon on a tile of colour, such as the category of a bill, or on a quiet tile; and their icons. |
| `SvTopBar.kt` | `SvLargeTopBar`, `SvBrandTopBar`, `SvAccountButton`, `SvBrandMark`, `SvTopBar` | The top bars: of a main screen, with its large title; of the home, with the app's mark and the button that leads to the account and Settings; and of a screen opened from another, with a round back button. A title wraps onto more lines rather than being cut off, except a long name the app did not choose, such as a shop's, which keeps to one line and ends in an ellipsis. |
| `SvWorkingRow.kt` | `SvWorkingRow` | Takes the place of the main action while work is running: the status and a way to stop. |

### Floating bars

`SvBottomBar` and `SvNavBar` go in the `bottomBar` of a screen's `Scaffold`, and the list of the
screen takes the padding that the `Scaffold` passes as `Modifier.padding`, not as `contentPadding`.
The list then ends above the bar, and nothing scrolls under it. A row half hidden behind a bar would
leave only a thin strip of itself to tap, which Android's accessibility checks report as a touch
target too small, and a field pushed up by the keyboard would end under the bar. A screen without a
`Scaffold` can instead put `SvBottomBar` last in a column below the part that scrolls, with the same
effect.

What is in the bottom bar takes the bar's colours by itself, so a screen never passes a colour
there: see [Where a component sits](#where-a-component-sits).

### Fields

`SvTextField` is the one field to type into. Its label stands above its box and its supporting text
below it, and a screen reader reads both with the field, which is also how a test finds it.

- What is typed is cut at `maxLength` characters, `SV_TEXT_FIELD_MAX_LENGTH` unless the field
  passes its own. A field of more than one line is cut at it too, so it passes a cap of its own,
  such as the 500 characters of a payment method's details.
- `keyboardOptions` reach the keyboard as they are given. Turning auto-correct off only stops the
  keyboard from changing words: it still suggests words and may learn what is typed. A value a
  keyboard must not learn, such as an account number, asks for a password keyboard
  (`KeyboardType.Password`), which keyboards neither suggest for nor learn from; the field still
  shows what is typed.
- A password keyboard also makes the field a password to autofill, so a password manager would
  offer to save what is typed or fill a password in. With `autofill` off the field takes no part in
  autofill at all: no autofill service fills it, offers to fill it or is given what was typed to
  save. The account number, IBAN and UPI fields of a way to get paid turn it off.
- Its line is 26sp tall rather than the 24sp of body text, because the marks above stacked
  Vietnamese capitals, as in "Ẳ", reach above a line of 24sp and a field of one line cuts off what
  reaches past its line.

`SvFieldRow` puts fields side by side, such as the quantity, price and amount of an item, while what
each one holds fits, and one above the other when it does not, so a number in a narrow field is never
cut off. `SvSwitch`, `SvCheckbox` and `SvRadio` go in a row that is toggled or chosen as a whole, with
the text they belong to, so that a screen reader reads the row as one control.

### The catalog

Debug builds have a second launcher entry, "Split Vibe components", that shows every component on
one page with sample content (`app/src/debug/java/com/dantech/splitvibe/catalog/CatalogScreen.kt`).
The reference images `catalog-light.png`, `catalog-dark.png` and `catalog-large-text.png` are that
page, the last with text at twice its size, so a change to a token or a component shows up even
where no screen uses it.

### Adding a component

1. Put it in `components/`, named `Sv…`, with a comment that says when to use it.
2. Take colours from `MaterialTheme.colorScheme`, `SvTheme.palette` and the ground it lies on,
   distances from `SvSpacing`, depth from `SvElevation`. If a new value is needed, add it as a token
   first.
3. Give it the role and the label a screen reader needs, which is also how tests find it, and a
   touch target of at least 48dp.
4. Add it to the catalog, and to the table above.
5. Record the catalog's reference images again and look at the light, dark and large-text images
   before committing them; [testing.md](testing.md#screenshot-tests) has the commands.

## Accessibility

- Text contrast is checked by `ColorTokensTest` for both themes.
- Colour never says anything alone: a balance comes with "gets back" or "owes", a confirmed request
  with a label, a chosen tab with its state for a screen reader.
- Text at twice its size is part of the design, not an afterthought. Components that hold text in a
  given width give way instead of cutting it off: the navigation bar shows its icons only, the large
  amount shrinks and then wraps, `SvBesideOrBelow` and `SvFieldRow` put their parts one above the
  other, a dialog stacks its buttons, and a large title moves its actions below it. Text inside a
  fixed shape, such as the initials of an avatar, keeps the size of its shape. The reference images
  ending in `-large-text` show screens at twice the size, where nothing may be cut off.
- A person chip that removes someone reads as one button that says so, not as a tick box.
- A row that can be tapped is one button for a screen reader, says what it does, and carries its
  whole label itself. Compose leaves out text that is scrolled out of view or lies under a bar, so
  a row of which only an edge shows would otherwise have nothing to read.
- The device tests run Android's accessibility checks, on Android 14 and later, on every screen they
  tap on: a label on everything that can be tapped, touch targets of at least 48dp, and text
  contrast. They reach the four main screens, Settings, the new split, the consent to the AI, the
  result and the step that asks for the money; [testing.md](testing.md) says which tests run them.
- A button's label has to be real text. `SvPrimaryFab` lays out its icon and text by hand because
  Material's icon-and-text floating button hides the text from screen readers.
