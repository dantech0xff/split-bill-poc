# Scout: how the app writes money, numbers and dates today

Date: 2026-11-20 (UTC). Read-only scout of the repository at commit `0c11acd` on `feat/ui-redesign`, made for the phase that makes amounts and dates follow the phone's region. Nothing was built or run.

## Summary

1. `BillCurrency.format(minor, locale = TEXT_LOCALE)` is the only money formatter, and `TEXT_LOCALE` is fixed to `Locale.US` (`core/domain/src/main/kotlin/com/dantech/splitvibe/domain/TextLocale.kt:11`). No production caller passes the phone's locale.
2. The formatter itself already works with any locale: it uses `NumberFormat.getCurrencyInstance(locale)` with the currency's own fraction digits. `BillCurrencyTest` already asserts Vietnamese and Indonesian output when a locale is passed.
3. Dates go through `dateText()` (`app/src/main/java/com/dantech/splitvibe/text/Dates.kt:9-11`), a medium date in `TEXT_LOCALE`, for example "Nov 19, 2026". Two places hard-code `Locale.ENGLISH` with their own patterns.
4. Typed numbers are read by `BillCurrency.parse` and three regular expressions. All of them accept both "." and "," whatever the phone's locale. Edit fields show a plain "." decimal with no grouping.
5. Only the CSV export follows the phone today (`Exporter.kt:48`, `CsvExport.kt:22-23`), plus the default of the device currency. The web pages use `Intl` with "en", and the Worker's push text is fixed English without amounts.

## 1. Money

`core/domain/src/main/kotlin/com/dantech/splitvibe/domain/BillCurrency.kt`:

- `data class BillCurrency(code, fractionDigits)` (line 18); amounts are `Long` minor units.
- `format(minor: Long, locale: Locale = TEXT_LOCALE)` (line 60): formats the major amount with `formatterFor(locale)`, and for a code that is not an ISO currency returns `"$number $code"` (line 62).
- `formatterFor(locale)` (lines 65-77): `NumberFormat.getCurrencyInstance(locale)` with `currency = iso` for an ISO code, otherwise `getNumberInstance(locale)`; minimum and maximum fraction digits are the currency's own. One formatter is cached per thread and locale (line 57).
- `inputText(minor)` (line 51): `toMajor(minor).toPlainString()`, the text an edit field shows.
- `parse(text)` (lines 32-48): strips spaces; accepts digits, "." and ","; the last separator is the decimal mark only when 1 to `fractionDigits` digits follow it, otherwise every separator is grouping. "12,5" USD is 12.50, "1.234,56" is 1234.56, "65.000" VND is 65000.

Other display text:

| Location | What it writes |
|---|---|
| `domain/ExchangeRate.kt:48`, `:70-75` | "1 USD = 25,350 VND"; `rateText` takes a locale, default `TEXT_LOCALE` |
| `domain/SplitCalculator.kt:238` | `percentText`: plain decimal with "." plus "%" |
| `domain/SplitEdits.kt:50`, `:58`, `:333`, `:391` | sentences with amounts; quantity as a plain decimal; takes a locale, default `TEXT_LOCALE` |
| `domain/payments/PaymentRequestBuilder.kt:60-62` | "Amount: …" with a locale parameter, default `TEXT_LOCALE` |
| `domain/Payment.kt:45-47`, `domain/SplitCalculator.kt:140`, `:144`, `domain/Bill.kt:73-80` | sentences through `currency.format` |
| `app/.../text/UiText.kt:51`, `:76-77` | ICU `MessageFormat` and `String.format` in `TEXT_LOCALE`, used for counts and plurals |
| `app/.../ui/split/DivisionInputs.kt:35` | percentage shown and edited with "." |
| `app/.../data/account/AccountStore.kt:81` | number of AI calls as a plain decimal |
| `app/.../ui/split/ResultContent.kt:564` | quantity as a plain decimal |

Callers of `BillCurrency.format` in `app/src/main`, none passing a locale: `ui/split/ResultContent.kt` (13), `ui/insights/InsightsScreen.kt` (7), `ui/groups/GroupScreen.kt` (6), `ui/split/RequestMoneyContent.kt` (5), `ui/editor/BillEditorContent.kt` (3), `ui/split/DivisionInputs.kt` (3), `export/PdfSummary.kt` (3 and its `money()` helper), `ui/requests/RequestsScreen.kt` (2), `ui/requests/RequestMessages.kt` (2), `ui/split/SplitSheets.kt` (2), and one each in `ui/claim/ClaimScreen.kt`, `ui/groups/AddToGroupSheet.kt`, `ui/history/HistoryScreen.kt`, `ui/recurring/RecurringScreen.kt`, `ui/requests/RequestLinkScreen.kt`, `ui/requests/RequestMoneyViewModel.kt`.

`core/designsystem` formats nothing itself: `SvAmount` takes `format: (Long) -> String` (`motion/SvAmount.kt:31`) and uses it for every count-up frame (line 57) and for the accessibility text (line 59).

Text that leaves the phone: the share summary and share card (`ui/split/RequestMoneyContent.kt:205-325`, `share/ShareCardRenderer.kt:61-62`), payment request and reminder messages (`ui/requests/RequestMessages.kt:28-49`), the PDF summary (`export/PdfSummary.kt`). The app sends the proxy minor units and currency codes only (`proxy/src/input.ts:189-211`, `proxy/src/requests.ts:125-141`).

## 2. Dates

Only `java.time.format.DateTimeFormatter` is used; there is no `SimpleDateFormat`, `DateUtils` or month-name array.

| Location | Formatter | Example |
|---|---|---|
| `app/.../text/Dates.kt:9-11` | `ofLocalizedDate(MEDIUM).withLocale(TEXT_LOCALE)` | "Nov 19, 2026" |
| `ui/claim/ClaimScreen.kt:497-498` | same style, locale parameter defaulting to `TEXT_LOCALE`, system time zone | |
| `export/Exporter.kt:142` | `ofPattern("LLLL yyyy", TEXT_LOCALE)` | "November 2026" |
| `data/account/AccountStore.kt:61` | `ofPattern("d MMMM", Locale.ENGLISH)`, UTC | "1 December" |
| `ui/paywall/ProTexts.kt:59` | `ofPattern("d MMMM yyyy", Locale.ENGLISH)` | "1 December 2026" |
| `ui/recurring/RecurringScreen.kt:162` | weekday name in `TEXT_LOCALE` | "Monday" |
| `ui/recurring/RecurringScreen.kt:180`, `RepeatBillSheet.kt:107`, `ui/groups/GroupScreen.kt:430`, `ui/history/HistoryScreen.kt:219`, `export/PdfSummary.kt:199-201`, `notifications/Notices.kt:117-118` | `dateText` | |

The date printed on a scanned bill is shown as the text the bill carried (`ui/split/ResultContent.kt:518`), not as a formatted date.

## 3. Typed numbers

`app/src/main/java/com/dantech/splitvibe/ui/common/InputFields.kt`: `NumberField` (lines 65-95) keeps the text as typed, calls the `parse` function it was given, marks the field as wrong when parsing fails, and uses `KeyboardType.Decimal` with at most 20 characters.

| Field | Call site | Parser | Text shown for a stored value |
|---|---|---|---|
| Split by amount | `ui/split/DivisionInputs.kt:85-89` | `BillCurrency.parse` | `inputText` |
| Split by percent | `DivisionInputs.kt:76-83` | `parsePercent` (42-49), "." or "," | `percentText` (35) |
| Item quantity | `ui/editor/BillEditorContent.kt:219-222` | `positiveQuantity` (344-352), "." or "," | plain decimal |
| Item amount, unit price, charge, bill total | `BillEditorContent.kt:226-238`, `:269-272`, `:298-304` | `BillCurrency.parse` | `inputText` (341) |
| Exchange rate | `ui/split/SplitSheets.kt:361-373` | `positiveRate` (388-395), "." or "," | `rateText(Locale.ROOT)` without grouping (364) |

No production code parses a formatted amount with a currency symbol back into a number.

## 4. Tests that pin the output

| File | What it asserts |
|---|---|
| `app/src/test/java/com/dantech/splitvibe/text/NumbersInTextsTest.kt:105-114` | "₫250,993", "$12.50", "Nov 19, 2026" and count sentences, on phones set to Persian and Arabic (qualifiers at 67-79, `Locale.setDefault` at 61-64 and 172-177) |
| same, `:136-169` | builds `ProScreen`, `SettingsScreen`, `HomeScreen`, `HistoryScreen` and checks that no text holds a digit other than 0 to 9 |
| `core/domain/src/test/kotlin/com/dantech/splitvibe/domain/BillCurrencyTest.kt:75-93` | "$12.50"; Vietnamese output holds "70.000" and "₫"; Indonesian holds "33.334"; "12.50 ABC"; "₫250,993" with the default locale set to `fa-IR` and `ar-EG`; "1 USD = 25,350 VND" |
| same, `:104-134` | the parse table and the round trip through `inputText` |
| `app/src/test/java/com/dantech/splitvibe/export/PdfSummaryTest.kt:101`, `:122` | "IDR1,234,567,890.12", "₫1,975,308,624" |
| `app/src/test/java/com/dantech/splitvibe/data/payments/PaymentPayloadTest.kt:55`, `:60` | "Amount: ₫71,500" |
| `app/src/test/java/com/dantech/splitvibe/ui/ComponentBehaviourTest.kt:209`, `:216` | content description "Bia Saigon Special, 132,000, Minh" |
| `app/src/test/java/com/dantech/splitvibe/recurring/RecurringBillWorkerTest.kt:109-110` | "Nov 17, 2026", "Dec 1, 2026" |
| `app/src/test/java/com/dantech/splitvibe/export/CsvExportTest.kt:29-36`, `:139` | decimal comma or point by locale |

The device journeys call the app's own formatter instead of a literal (`app/src/androidTest/.../e2e/JourneySteps.kt:112-114`). No Gradle test task sets a locale: `core/domain/build.gradle.kts` has no `systemProperty`, and `app/build.gradle.kts:174-184` sets only the screenshot properties.

## 5. Outside the app

- Web: `web/src/money.ts:19-34` uses `new Intl.NumberFormat("en", …)`; `web/src/dates.ts:2-13` uses `Intl.DateTimeFormat("en", { dateStyle: "medium", timeZone: "UTC" })`; `web/src/share-state.ts:82` uses `Intl.ListFormat("en")`. `navigator.language` is never read.
- Worker: push text is fixed English with no amount or date (`proxy/src/push.ts:15-25`).

## 6. Locale settings

- `android:supportsRtl="true"` (`app/src/main/AndroidManifest.xml:37`); no `localeConfig`; no resource filter; pseudo-locales on the debug build only (`app/build.gradle.kts:136-138`). The only resource folder for text is `values/`, so every text is English.
- The device currency defaults to the currency of `Locale.getDefault()` (`data/settings/SettingsStore.kt:89-97`).

## Unresolved questions

1. Whether the rows asserted by `app/src/test/java/com/dantech/splitvibe/RecordedSampleBillTest.kt:36-39` ("22.000", "902.664") come from the recorded bill text or from the app's formatter.
2. The value type and text of `ShareStepper` (`ui/split/DivisionInputs.kt:115`).
