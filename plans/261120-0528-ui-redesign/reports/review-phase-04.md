## Code review: phase 4 (amounts, numbers and dates follow the phone's region)

Nothing in the change writes a wrong amount or value. Everything on the "must not change" list stays the same. The concerns are:
- one date-order consequence of the phase's own spec that needs the owner's decision,
- one gap in the device test,
- old-format text that lingers after the user changes region,
- a small field/amount decimal-mark mismatch in a few regions,
- several tests that cannot fail.

**Scope.** 30 modified files plus the new `/home/user/split-bill-poc/core/domain/src/main/kotlin/com/dantech/splitvibe/domain/DateTexts.kt` (+637/−110, plus 72 new lines). I read the diff and every changed file in context, traced every caller of the functions whose default changed, and read the proxy's validation and request routes.

**JDK checks.** I wrote three small Java programs that copy the new Kotlin logic and ran them on JDK 21 (not Gradle, nothing in the project touched):
- `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/probe/Probe.java`
- `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/probe/Probe2.java`
- `/tmp/claude-0/-home-user-split-bill-poc/f8110399-95a0-5f4c-9986-bbd080ac93f5/scratchpad/probe/Probe3.java`

ktlint, lint and the tests were not run.

### Findings, most severe first

**1. Medium (spec-level, owner decision): the order rule puts the year first in English dates for regions that write dates with a month name another way.**
- **Where:** `/home/user/split-bill-poc/core/domain/src/main/kotlin/com/dantech/splitvibe/domain/DateOrder.kt:28-35` (already committed), used by `DateTexts.kt:54`. Pinned by `RegionFormatsTest.kt:44` and `:170`.
- **Region data (JDK 21 = CLDR 42, the same data as Android 14):**

| Region | SHORT pattern (used by the rule) | MEDIUM pattern (writes a month name) |
|---|---|---|
| en-CA | "y-MM-dd" | "MMM d, y" |
| en-ZA | "y/MM/dd" | "dd MMM y" |
| fr-CA | "y-MM-dd" | "d MMM y" |
| sv-SE | "y-MM-dd" | "d MMM y" |
| fa-IR | "y/M/d" | "d MMM y" |

- **Example, a Canadian English phone:** the history list shows "2026 Nov 19", the bill link "Open until 2026 Nov 27", and Pro "Renews on 2026 December 17.". The test pins fa-IR as "2026 Nov 13".
- **Not a deviation:** this follows the phase, which names `FormatStyle.SHORT`, and the planner filled in these cells.
- **Options:**
  - (a) Keep SHORT and accept these regions.
  - (b) Read the order from `FormatStyle.MEDIUM`. It is a one-line change; of the eight tested regions only fa-IR changes class (my-MM flips the other way). It needs a device re-run on 8.0 to 16.
  - (c) Keep a hand-written exception list (not recommended).
- I recommend (b) once the owner approves.

**2. Medium: the device test cannot catch a failed English-symbol override, and its new checks have not run anywhere.**
- **Where:** `/home/user/split-bill-poc/app/src/androidTest/java/com/dantech/splitvibe/e2e/DateOrderOnDeviceTest.kt:29-39`, testing `/home/user/split-bill-poc/core/domain/src/main/kotlin/com/dantech/splitvibe/domain/BillCurrency.kt:95-99`.
- **Problem:** it only checks VND, whose symbol is "₫" in both English and Vietnamese, so it passes whether or not the override works. The override is the riskiest difference between Android and the JDK: on Android the formatter is backed by ICU, and the JDK tests can't show that ICU keeps a custom symbol. Probe3 confirms that without the override vi-VN writes "1.234,50 US$".
- **What goes wrong:** if some Android version ignores the custom symbol, phones in Vietnam show "US$" and phones in France "$US", and every test stays green.
- **Fix:** add a check that `BillCurrency("USD", 2).format(123_450)` on a vi-VN phone contains "$" and not "US$".
- **Status:** you said the date-order test passed on 8.0, 11, 14 and 16. These amount checks are uncommitted and have never run, so phase steps 3 and 10 are still open.

**3. Low: texts built once keep the old region's marks after a region change.**
- **Where:**
  - `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/ui/settings/SettingsScreen.kt:419-420` (`CALL_COSTS`, a top-level `val`)
  - `/home/user/split-bill-poc/app/src/main/java/com/dantech/splitvibe/assistant/SplitAssistant.kt:81` (`WORDS.cost`, a companion `val`)
  - Text held in ViewModels, which survive the Activity restart: `SplitAssistantViewModel.kt:452-453`, `:469`, `SplitBillViewModel.kt:109`
- **Example:** start the app on a US phone, then switch the region to Vietnam. The quota message (`ProxyModelCall.kt:173-183`) reads "Changing a split takes 0.2 of a call, and 0,4 of this month's 10 calls are left. They come back on 1 December." Settings shows "…takes 0.2." under "2,8 of 3 calls left this month."
- **Why it matters:** the phase assumes the Activity restart is enough, and the `regionLocale` doc (`TextLocale.kt:20-21`) promises the new way "from then on". That only holds for text built while the screen is drawn.
- **Fix:** make `CALL_COSTS` a getter, and build the cost text when the message is built (for example `cost: () -> UiText`).
- **Tests:** `SplitAssistantTest.kt:130` expects "0.2". It is safe today only because the Vietnamese gallery test runs in a different Robolectric sandbox (sandboxes are split by graphics mode, and 4.17 defaults to LEGACY; I checked the 4.17 jar).

**4. Low: money fields use the region's plain decimal mark, while amounts use its money decimal mark.**
- **Where:** `/home/user/split-bill-poc/core/domain/src/main/kotlin/com/dantech/splitvibe/domain/TextLocale.kt:64-65`, through `BillCurrency.kt:55`.
- **Example (fr-CH, JDK 21):** an amount reads "1 234.50 $" but its field shows "1234,50". Both parse to the same value, so nothing is ever wrong, but the field does not show the amount's mark.
- **Fix:** for ISO currencies in `inputText`, use `monetaryDecimalSeparator` with the same "." or "," guard.

**5. Low: tests weaker than they look.**
- **a.** `/home/user/split-bill-poc/app/src/test/java/com/dantech/splitvibe/text/NumbersInTextsTest.kt:129-134` takes the expected date order from `dateOrderOf(phone)`, the same function `dateText` uses, so a wrong class for fa or ar would pass. Use literals ("2026 Nov 19" for fa, "19 Nov 2026" for ar). Also, at `:120-128` and `BillCurrencyTest.kt:91-102`, fa and ar with Latin digits use "." and "," (confirmed by probe), the same as English. Those mark checks would pass against the old English output; only the digit and date checks can fail.
- **b.** `/home/user/split-bill-poc/core/domain/src/test/kotlin/com/dantech/splitvibe/domain/RegionFormatsTest.kt:141` gets the expected marks from `regionLocale()`, the code under test. If it broke back to English, the five-region test would still pass. Use `Locale.forLanguageTag("$tag-u-nu-latn")`.
- **c.** `RegionFormatsTest.kt:53-63` only changes the DISPLAY locale, and `Locale.getDefault()` stays ar-EG (confirmed by probe). Code that read `Locale.getDefault()` instead of the FORMAT locale would still pass. Also set FORMAT to Germany and expect "de-DE-u-nu-latn".
- **d.** `RegionFormatsTest.kt:214-228`: "the three parts join to `format`" and the digit-boundary checks are true by construction, because both functions slice the same formatter output (`BillCurrency.kt:71`, `:81-85`). Only the "₫ outside the number" check can fail. It works as a regression guard but proves nothing about device output.

**6. Nits**
- `ExchangeRate.kt:74` is 142 characters (the limit is 140). `BillEditor.kt:57` is already 152, so ktlint likely tolerates comment lines; run ktlintCheck to be sure.
- `RegionFormatsTest.kt:316` writes the no-break space as a raw character. The bytes are correct; `" "` is safer, as `ScreenGalleryTest.kt:525` does.
- The doc at `BillCurrency.kt:163` shows " ₫" with a plain space; the real suffix is a no-break space.
- `SplitErrorsTest.kt:179` still has the fixture text "come back on 1 December".
- `dateOrderOf` is uncached and runs for every date written, and `numberText` builds a new formatter on every call.
- ClaimScreen's `locale` parameter only drives the date order; amounts on that screen ignore it.

### Checked and found correct
- **QR codes and payment links:** they write amounts with `toPlainString`, and EmvQr uses `Int.toString`/`uppercase()`. `RegionFormatsTest:260-283` shows they are identical in every region.
- **Server data:** numbers go up as minor units and codes (quantity and rate as plain text). Request ids are built from minor units. The proxy accepts no-break spaces and direction marks in details (`proxy/src/input.ts:271-312`). A repeated request id returns the stored row without comparing the body (`proxy/src/requests.ts:205-206`), so a retry after a region change cannot conflict.
- **CSV export:** untouched. The parsers and the three-decimal "1.234" ambiguity are unchanged.
- **Region source:**
  - It is read at call time from the FORMAT locale, and Latin digits are forced even over an explicit `nu-arab`.
  - It is cached in a volatile, immutable pair compared by equality, so it is safe across threads.
  - An ill-formed locale throws and falls back to English (probe).
  - Calendar keywords (Buddhist, Japanese, Persian, Islamic) change neither the order nor the year.
- **English symbol on the JDK:** vi-VN writes "US$" without the override and "$" with it.
- **Three parts:** `SvHero.kt:124-131` joins them verbatim. The split is right with direction marks, no-break and narrow no-break spaces, U+2212 minus and ’ (ar-EG, fa-IR, he-IL, fr-FR, nl-NL, de-CH, sv-SE).
- **Fields:** all six number fields show ungrouped text with "." or ",". `NumberField` compares parsed values (`InputFields.kt:85`), so the mark doesn't fight the user's typing. Writing a value into a field and reading it back gave the same value in all 25,632 combinations tried (every JDK locale, 0 to 3 decimals, six values).
- **Dates:**
  - All four forms match the phase table, and "November 2026" is the same in every order.
  - Short dates drop the year only within the current year, and `today` is a required parameter.
  - Scanned-bill dates and rate dates are still shown raw.
  - No `Locale.ENGLISH` or `ofLocalizedDate` is left in main code, and US output is unchanged.
- **Tests:**
  - Only the expectations the phase lists were changed.
  - The en-US system properties reach the test JVM's command line, so the format locale is en-US too.
  - The Vietnamese gallery test follows the large-text test and asserts the region and the total before drawing. Its on-screen total should match exactly one node.
  - `app/src/test/screenshots` is clean.
- **Scope:** `SplitEdits.kt:284` (`titlecase(TEXT_LOCALE)`) is a needed fix, since a Turkish region would otherwise write "İ". Nothing else is outside the phase. Removed imports leave no dangling references.

### Plan follow-ups (I did not edit the plan)
Todo items 2 to 9 look done. Item 1's recorded device results and item 10 (test gate, device runs on the final commit at API 34 and 26, screenshots for the owner) are open. The [UNVERIFIED] marks and Todo boxes in the phase file are unchanged.

### Unresolved questions
1. The owner's choice on finding 1 (SHORT or MEDIUM; year first or day first for fa-IR).
2. The results of the new device checks at API 26 and 34, after adding the USD check.
3. Not a regression: the rate parser reads "25.350" as 25.35 in every region, and vi-VN and de-DE now display rates as "25.350". Group conversions save amounts at that rate. Should a guard follow later?
4. The owner hasn't yet confirmed that the "Amount:" line on the web page carries the sender's format (`plan.md:64`, Risk Assessment).

Status: DONE_WITH_CONCERNS
Summary: Nothing in the change writes a wrong value, and payment codes, server data, the CSV export and the parsers are unaffected. The open points are the SHORT-pattern date order (an owner decision), a device check that can't catch a broken currency-symbol override, text that keeps the old region's format after a region change, and several tests that can't fail.
Concerns/Blockers: The new device checks have never run, and I ran no Gradle tasks; please run the test gate and ktlintCheck.
