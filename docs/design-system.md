# Design system

How Split Vibe looks, and the rules that keep it looking that way. Everything described here lives
in the `:core:designsystem` module. The reference images in `app/src/test/screenshots` show the
result on every screen, in the light and the dark theme.

## The one rule

Screens decide nothing about the look themselves. They use the components and tokens below and
never set a theme, a colour value, a shadow or an animation timing of their own, and they never
opt in to an experimental Expressive API. `DesignSystemBoundaryTest` fails when a screen does, and
names the file and line.

The reason is practical. The app uses a release candidate of Material 3 Expressive, so when the
library changes, only one module has to follow.

## Principles

1. **One main action per screen.** It is the large button in the bottom bar, or the floating button.
2. **Amounts are the hero.** They use the amount text styles, with digits of equal width, and they
   are right-aligned in lists so that a column of amounts lines up.
3. **Group by space and surface, not by lines.** Related rows sit together on a tinted surface.
   There are no divider lines.
4. **One accent colour.** Violet marks what can be acted on and what matters most. Other colours
   carry meaning: a person, a notice, an error.
5. **Depth is elevation.** A thing that floats above the page gets an elevation level, never a
   shadow of its own.

## Theme

`SplitVibeTheme` wraps Material 3 Expressive with the violet palette and the "expressive" motion
scheme. `MainActivity` applies it once. It follows the system's light or dark setting; pass
`darkTheme` to force one, which the tests do.

## Colour

Every colour role takes a fixed lightness step, called a tone, of one of five ramps: accent, muted
accent, raspberry, neutral and tinted neutral. A text role and its background are always at least
50 tones apart.

| Role | Light | Dark | Used for |
|---|---|---|---|
| `primary` | `#554ACC` | `#CEBDFF` | Main buttons, links, the selected state |
| `primaryContainer` | `#E7DEFF` | `#3433B0` | The card a screen is about, such as the bill total |
| `tertiary` | `#A53167` | `#FFB0CD` | A second accent, used sparingly |
| `tertiaryContainer` | `#FFD8E5` | `#89104F` | A notice |
| `surface` | `#FAF8FF` | `#151219` | The page |
| `surfaceContainer` | `#EFECF6` | `#201F25` | Grouped list rows |
| `surfaceContainerLowest` | `#FFFFFF` | `#100D15` | Floating cards and bars in the light theme |
| `onSurface` | `#1C1B21` | `#E4E1EA` | Text |
| `onSurfaceVariant` | `#494455` | `#C9C4D8` | Secondary text |

The full list is in `tokens/SvColors.kt`. `ColorTokensTest` checks that text has a contrast of at
least 4.5:1 on every background the palette pairs it with, and outlines at least 3:1. Change a
value and the test tells you whether text is still readable.

People have six colours of their own, spread around the colour wheel and leaving out violet.
`SvAvatar` picks one from the person's name, so the same person always has the same colour.

## Type

The type scale is Material's, including its emphasized styles. Three more styles are for amounts,
read from `MaterialTheme.typography`:

| Style | Use |
|---|---|
| `amountLarge` | The one amount a screen is about, such as the bill total |
| `amountMedium` | An amount a list row is about, such as what a person pays |
| `amountSmall` | An amount that supports something else, such as the price of one item |

## Spacing

Distances come from `SvSpacing`, a 4dp grid: `xs` 4, `sm` 8, `md` 12, `lg` 16, `xl` 24, `xxl` 32.
`lg` is the margin between content and the edge of the screen.

## Shape

Shapes come from `MaterialTheme.shapes`. `SvShapes` adds the two corner sizes of grouped rows: 20dp on
the outside of a group and 4dp where two of its rows meet.

## Elevation

`SvElevation` has four levels. In the light theme a level is a soft shadow tinted with the darkest
violet, made of a wide faint layer and a tighter one below. In the dark theme shadows cannot be
seen, so a level is a lighter surface instead and nothing is drawn around it.

| Level | Use |
|---|---|
| `Level1` | Rests on the page: a card |
| `Level2` | Slightly lifted: the card a screen is about |
| `Level3` | Floats over content: the bottom bar, the floating button |
| `Level4` | On top of everything: a dragged item |

Use `Modifier.elevation(level, shape)` before the background of the same shape, and
`level.surface` for the colour. The components below already do both.

A Material component that draws a shadow of its own gets it switched off and takes a level
instead, so every shadow in the app looks alike. Where Material offers no way to switch it off,
as for the button of the floating menu, the design system draws that part itself.

Only the strength of a shadow may be animated, through the `strength` parameter. It changes
opacity and offset, which are cheap to redraw every frame. The blur radius is fixed per level
because redrawing it every frame is expensive.

## Motion

Movement has to explain something: where a thing came from, or that a tap was received. Screens
never write an animation themselves; they use these pieces, which take their timing from the
theme's motion scheme.

| Piece | What it does |
|---|---|
| `SvAmount` | An amount that runs through the numbers in between when its value changes, and can count up from zero when it first appears. A screen reader always gets the real amount. |
| `Modifier.staggeredEntry` | Lets the rows of a screen slide up and fade in one after another when the screen appears. Rows that scroll into view later simply appear. |
| `SvWorkingRow` | Fades from one status line to the next while work is running. |
| `SvSuccessMark` | Pops its check in once, with one short confirming vibration. |
| `SvBillPhoto` | A new photo settles into its frame, growing from a smaller and rounder card. While the bill's text is read, a band of light runs over the photo. |
| `Modifier.bounceOnChange` | A quick springy bounce each time a value changes, such as a person chip switched on or off. Nothing moves when the element first appears. |
| `Modifier.popIn` | The same bounce for an element added to a screen that is already shown, such as a person added to a split. What is there when the screen appears does not pop. |
| `rowMotion` | A row of a lazy list fades in when it is added, fades out when it is removed, and slides to its new place when the rows above it change. The row needs a key of its own. |
| `Modifier.sharedSurface` | Marks a surface that grows into the surface with the same name on the next screen, and shrinks back on the way back. The button that opens a split and the split screen are one such pair. |
| `SvSteps` | Shows one step of a flow at a time and fades between the steps. A shared surface in one step turns into the one with the same name in the next: the button that sends the requests shrinks into the round mark that confirms them. |
| `Modifier.keepSizeDuringTransition` | Keeps a label at its own size while the surface around it changes shape, so the edge cuts it off instead of it wrapping onto more lines. `SvPrimaryButton` uses it. |
| `SvMotion.screenChange` | The timing of everything that happens while one screen or step turns into another. |

A change of screen has a fixed length instead of a spring. A back swipe scrubs through the change
with the finger, and only parts of equal length stay together and follow the finger evenly; a
spring would be over after the first part of the swipe and leave nothing to preview.

Some people switch animations off in the system settings. Compose then ends ordinary animations at
once by itself. Effects that never end and delays written by hand cannot be known to it, so they
ask `LocalReducedMotion` and stand still or are skipped: the loading indicator stands still, the
band over the photo is not drawn, and bounces are skipped. `ReducedMotionTest` checks that nothing
moves on its own in that case and that a bill can still be split from start to finish.

## Components

| Component | Use |
|---|---|
| `SvLargeTopBar` | The top bar of a main screen. The large title shrinks as the content scrolls. |
| `SvCard` | A surface that floats at an elevation level. Pass a colour when the colour carries meaning. |
| `SvSectionHeader` | The title above a group, with an optional hint about what can be done there. |
| `SvListRow` | One row of a group. Give it its position in the group and it rounds the right corners. Works as separate items of a lazy list. A row that can be tapped also takes a label: everything the row says, in one sentence. |
| `SvAvatar` | A person's initials on that person's colour. Decorative: always show the name beside it. |
| `SvMessageCard` | A notice or an error inside the page, with an optional action. |
| `SvLoadingIndicator` | Work of unknown length is running. Stands still when animations are off. |
| `SvWorkingRow` | Takes the place of the main action while work is running: the status and a way to stop. |
| `SvSuccessMark` | Marks that something has just succeeded: a round accent badge with a check. A round button can turn into it. Decorative: say what succeeded in text. |
| `SvBillPhoto` | The photo of a bill in a frame of its own shape. Pass `scanning` while its text is read. |
| `SvPrimaryButton` | The main action as a large button across the full width. It is fully round, so it can turn into a round surface. |
| `SvBottomBar` | The floating bar at the bottom that holds the main action. |
| `SvPrimaryFab` | The main action as a floating button, for a screen that is mostly a list. |
| `SvFabMenu` | The main action as a floating button that opens a short menu, for a main action that can start in a few ways. The screen keeps whether it is open and closes it on back. |
| `SvSplitButton` | A main action with a few close alternatives behind an arrow. |
| `SvChoiceGroup` | One option out of a few, as joined buttons that a screen reader reads as radio buttons. |
| `SvFloatingToolbar` | A short row of actions of equal weight that floats over the content. |
| `SvPersonChip` | A person as a chip with their colour: one that can be switched on and off, or one that was added and is removed with a tap. |
| `SvPersonPickRow` | A person who can be ticked in a list, such as in a sheet. One tick box for a screen reader, with its own label. |
| `SvBottomSheet` | A sheet from the bottom for a short task: a title, the content, and the actions at the bottom. It follows the finger and previews the screen behind on a back swipe. |
| `SvEmptyState` | Takes the place of something that is not there yet, with a soft round icon and a sentence. |

### The catalog

Debug builds have a second launcher entry, "Split Vibe components", that shows every component on
one page with sample content (`app/src/debug/.../catalog/CatalogScreen.kt`). The reference images
`catalog-light.png` and `catalog-dark.png` are that page.

### Adding a component

1. Put it in `components/`, named `Sv…`, with a comment that says when to use it.
2. Take colours from `MaterialTheme.colorScheme`, distances from `SvSpacing`, depth from
   `SvElevation`. If a new value is needed, add it as a token first.
3. Add it to the catalog, and to the table above.
4. Record the reference images again: `./gradlew :app:testProdDebugUnitTest -Pscreenshots=record`.
5. Look at the light, dark and large-text images before committing them.

## Accessibility

- Text contrast is checked by a test for both palettes.
- The four main screens are drawn with text at twice its size, and nothing may be cut off there.
  Text inside a fixed shape, such as avatar initials, does not grow with the text size.
- A row that can be tapped is one button for a screen reader, says what it does, and carries its
  whole label itself. Compose leaves out text that is scrolled out of view or lies under a bar, so
  a row whose edge shows above the bottom bar would otherwise have nothing to read.
- The device tests check every screen they reach with Android's accessibility checks: a label on
  everything that can be tapped, touch targets of at least 48dp, and text contrast. They reach the
  home screen, settings, the new split, the result and the sent requests.
- A button's label has to be real text. `SvPrimaryFab` lays out its icon and text by hand because
  Material's icon-and-text floating button hides the text from screen readers.
