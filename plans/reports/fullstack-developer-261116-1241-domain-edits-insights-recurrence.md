# Domain: split edits, insights and recurring bills

Date: 2026-11-16 (UTC). Branch `feat/phase-07-ai-assistant-insights`: six domain commits on top of `594eb35`, interleaved with the proxy's, then this report. All pushed.

## Outcome

Everything the domain part of phase 7 asked for is built in `:core:domain`, and every check passes.

- **Categories.** Bills carry one of the nine categories of the contract, and the bill analysis answer's `category` is read; a missing or unknown one is `OTHER`.
- **Split edits.** The thirteen ops of `/v1/split-revision` are a sealed `SplitEdit`. A parser reads the proxy's final answer without ever throwing. `SplitEdits.apply` applies the edits in order or rejects each with a short English reason, and `SplitEdits.describe` writes one sentence per edit.
- **The request.** `SplitRevisionRequest.body` builds the body exactly as the contract shows it, without `requestId`. A test compares it with the contract's example.
- **Insights.** `Insights.of` adds up bills by month, person, group and category in the local currency. A 20-bill fixture matches a table worked out by hand.
- **Recurrence.** Weekly and monthly schedules give the occurrences up to a day, the next occurrence, the period key and the draft id, with the contract's clamping to the month's last day.

No app file changed. Adding `Bill.category` kept `:app` compiling, and no app test needed a new expected value.

| Check | Result |
|---|---|
| `:core:domain:test` | 185 tests pass (134 before) |
| `ktlintCheck` | Passes |
| `:app:compileProdDebugKotlin` | Passes |
| `:app:testProdDebugUnitTest` | Passes (227 tests, unchanged) |

Before each commit, ktlint and the domain tests ran on its tree. The app checks ran before every commit that touched an existing domain type. The parser commit only adds files and was not checked against the app on its own, but the next commit's app run covers it. Last, all four checks ran again on the final tree with `--rerun-tasks`: 412 tests passed, none failed, and no compiler warning comes from these files.

## Public API for the app

### Categories (`domain/BillCategory.kt`, `domain/Bill.kt`)

```kotlin
@Serializable enum class BillCategory(val label: String) {
    FOOD_AND_DRINK, GROCERIES, TRANSPORT, LODGING, ENTERTAINMENT, SHOPPING, UTILITIES, RENT, OTHER;
    val wireValue: String                                   // "food_and_drink", ...: the @SerialName
    companion object { fun fromWire(value: String): BillCategory? }
}
data class Bill(..., val lastChargeNumber: Int = 0, val category: BillCategory = BillCategory.OTHER)
```

The labels are "Food & drink", "Groceries", "Transport", "Lodging", "Entertainment", "Shopping", "Utilities", "Rent" and "Other". A stored bill without the field reads as `OTHER`. To change the category, use `bill.copy(category = ...)`.

### Edits (`domain/SplitEdit.kt`)

```kotlin
@Serializable sealed interface SplitEdit {
    data class ItemSharers(val itemId: String, val participantIds: List<String>)
    data class ItemShares(val itemId: String, val shares: Map<String, Long>)
    data class SetMode(val mode: SplitMode)                                    // op split_mode
    data class WholeShares(val shares: Map<String, Long>)
    data class WholePercent(val basisPoints: Map<String, Long>)
    data class WholeAmounts(val amounts: Map<String, Long>)
    data class AddPerson(val ref: String, val name: String)
    data class RemovePerson(val participantId: String)
    data class SetSharing(val participantId: String, val sharing: Boolean)
    data class AddCharge(val kind: ChargeKind, val label: String, val amountMinor: Long? = null, val percentBasisPoints: Long? = null)
    data class SetPayer(val participantId: String)
    data class ItemAmount(val itemId: String, val amountMinor: Long)
    data class ItemQuantity(val itemId: String, val quantity: BigDecimal)
}
```

Each subclass has the op's name as its `@SerialName`, so a proposal can be kept in `SavedStateHandle` with any `Json`.

### The answer (`llm/SplitRevisionParser.kt`)

```kotlin
data class SplitRevision(val edits: List<SplitEdit>, val explanation: String, val question: String?, val unreadable: Int)
object SplitRevisionParser {
    const val MAX_EDITS = 20
    const val MAX_EXPLANATION = 200
    fun parse(text: String): SplitRevision?   // null only when text is not a JSON object
}
```

### Apply and describe (`domain/SplitEdits.kt`)

```kotlin
object SplitEdits {
    fun apply(bill: Bill, plan: SplitPlan, edits: List<SplitEdit>): SplitEditResult
    fun describe(edits: List<SplitEdit>, bill: Bill, plan: SplitPlan, meName: String, locale: Locale = Locale.getDefault()): List<String>
    fun describe(edit: SplitEdit, bill: Bill, plan: SplitPlan, meName: String, locale: Locale = Locale.getDefault()): String
}
data class SplitEditResult(val bill: Bill, val plan: SplitPlan, val applied: List<SplitEdit>, val rejected: List<RejectedEdit>)
data class RejectedEdit(val index: Int, val edit: SplitEdit, val reason: String)
```

The screen should use the list form of `describe` with the same `(bill, plan)` it gives `apply`. That form describes each edit against the state the edits before it leave. So "Beer: shared by Dana and Zoe" names the person a ref adds, and "Add a 10% tip (₫31,000)" shows the amount that is really added. `RejectedEdit.index` lines a reason up with its sentence.

- **Undo.** Keep the `(bill, plan)` from before.
- **Dropping one change.** Apply the kept edits again, as the parser gave them, to that same `(bill, plan)`.

Two small additions to `SplitPlan` came with this: `withSharing(participantId, sharing)` and `withWholeDivision(division)`. `withMeSharingToggled` now calls `withSharing`.

### The request (`llm/SplitRevisionRequest.kt`)

```kotlin
object SplitRevisionRequest {
    const val MAX_INSTRUCTION_CODE_POINTS = 500
    const val DEFAULT_ME_NAME = "Me"
    fun body(instruction: String, bill: Bill, plan: SplitPlan, meName: String): JsonObject  // add "requestId" to it
}
```

`body` throws `IllegalArgumentException` when the trimmed instruction is not 1 to 500 code points long, so the app should limit the field to that.

### Insights (`domain/Insights.kt`)

```kotlin
data class InsightBill(val date: String?, val category: BillCategory, val group: String?, val currency: BillCurrency,
    val totalMinor: Long, val shares: List<InsightShare>, val myShareMinor: Long, val rate: ExchangeRate?)
data class InsightShare(val personKey: String, val name: String, val amountMinor: Long)
data class Spending(val bills: Int, val totalMinor: Long, val myShareMinor: Long)
data class PersonSpending(val personKey: String, val name: String, val bills: Int, val totalMinor: Long, val myShareMinor: Long, val shareMinor: Long)
data class Insights(val currency: BillCurrency, val overall: Spending, val byMonth: Map<YearMonth, Spending>,
    val byPerson: List<PersonSpending>, val byGroup: Map<String?, Spending>, val byCategory: Map<BillCategory, Spending>,
    val undated: Spending, val withoutRate: Map<String, Int>) {
    companion object { fun of(bills: List<InsightBill>, localCurrency: BillCurrency): Insights }
}
```

`SavedBill.exchangeRate` can be passed as `rate` as it is. The ordering is:
- months, earliest first;
- people, largest own part first;
- groups and categories, largest total first.

`BillCurrency.toMinor` and `ExchangeRate.convert` gained a `rounding` parameter that defaults to half up, so their existing callers are unchanged.

### Recurrence (`domain/Recurrence.kt`)

```kotlin
@Serializable enum class Frequency { WEEKLY, MONTHLY }                          // "weekly", "monthly"
@Serializable data class RecurrenceSchedule(val frequency: Frequency, val start: LocalDate)  // start stored as "2026-01-31"
object Recurrence {
    fun occurrence(schedule: RecurrenceSchedule, index: Long): LocalDate
    fun occurrences(schedule: RecurrenceSchedule, until: LocalDate): List<LocalDate>   // start..until, inclusive
    fun next(schedule: RecurrenceSchedule, after: LocalDate): LocalDate               // strictly after; the start before it
    fun periodKey(occurrence: LocalDate): String                                      // "2026-02-28"
    fun draftId(templateId: String, occurrence: LocalDate): String                    // "{templateId}_2026-02-28"
}
```

## Where I interpreted the contract

**Which edits switch a whole-bill split to by item.** Only `item_sharers` and `item_shares` do. `item_amount` and `item_quantity` change the bill through `BillEditor` and leave the way of splitting alone. Switching to by item to fix a price would throw away a split by shares or by percent.

**Whole-bill divisions.** `whole_shares`, `whole_percent` and `whole_amounts` replace the whole division, so anyone they leave out gets nothing. Their sums are checked by `SplitCalculator`, so the reasons are the calculator's own sentences, such as "Percentages add up to 90%...". Before that, each part is checked on its own: below zero, above 100%, or above the bill total. Then no sum of hostile numbers can overflow into exactly the total.

**People who do not share the bill.** A positive part for someone in `notSharing`, or naming them in an item edit, is rejected, for example "Khoa does not share the bill." It does not make them share again. A part of zero for them is accepted.

**Rejections beyond the contract's four kinds.** These all count as an invalid number or a sum that does not match:
- a charge with both or neither of `amountMinor` and `percentBasisPoints`;
- a percentage charge that comes to nothing;
- an edit that would take the bill total below zero, or above `MAX_AMOUNT_MINOR`;
- one person named twice, as a ref and as the id it became;
- a ref outside `new1` to `new9`, or one that is added twice;
- a blank name;
- a quantity that is not above 0 and below 1,000,000 with at most six decimals. That bound also keeps out values like `1E-999999999`, which would hang `BigDecimal` rounding.

**Problems an edit leaves behind are not rejections.** The following are applied exactly as the screens apply them, and the plan's problems then say what to fix:
- `remove_person` or `set_sharing` in a split by percent or by amount;
- `add_charge` in a split by amount, or with several payers;
- `set_sharing` false for the last person sharing.

A later edit in the same list can fix them. This is why the invariant is checked "when the plan has no problems", as the brief says.

**Smaller rules.**
- `split_mode` to the mode the plan already has changes nothing, as `withMode` does.
- Taking off someone added under a ref frees the ref, so a later edit naming it is rejected. Their `pN` id can be given to the next person added.

**Reading the answer.**
- An answer with both a question and edits keeps both; the contract says the edits are then empty. See the open questions.
- `edits` that is not an array gives no edits and counts one unreadable edit.
- Edits after the twentieth are counted as unreadable.
- Op names must match exactly. `mode` and `kind` are trimmed and read in any case.
- A quantity must be a JSON string, as the contract says. kotlinx accepts integers written in quotes.
- Names, labels, the explanation and the question lose control characters, line breaks and invisible formatting characters such as U+202E. The zero-width joiner and non-joiner stay, as they do on the proxy.
- They are then cut to length, in code points: names and labels to 80 (the app's text fields), the explanation to 200 (the contract) and the question to 500.

**The request.**
- Names and labels go as stored, without cleaning.
- A blank user name goes as "Me", so the model can still tell who "I" is.
- Divisions of items that are no longer on the bill are left out.
- Charge kinds go as their lower-case wire names. `ChargeKind`'s stored names stay as they are.

**Describing.**
- Sentences name the user by `meName`, or "you" when it is blank.
- Reasons say "You" for the user and never show a raw id.
- Article choice follows how the number is read: "an 8% VAT", "an ₫18,000 service charge".

**Insights.**
- Each amount of a bill is rounded on its own: its total, the user's part and each person's part. A bill's parts can therefore come to a unit off its converted total; bill 10 of the fixture shows this.
- A rate is used only when it goes from the bill's currency to the local one, is above zero and keeps every amount in range. Otherwise the bill counts in `withoutRate`.
- The same currency with other decimals converts at 1.
- Undated bills still count in people, groups, categories and the overall sum.
- A person is named as the first bill in the list names them, so the app should pass the newest bill first.

**Recurrence.** I made `RecurrenceSchedule` serializable, because templates are kept on the device. I added `draftId` so the id format lives in one place.

## Tests

Before: 134 domain tests. After: 185, all passing.

| File | Tests |
|---|---|
| `domain/BillCategoryTest.kt` | 2 |
| `llm/BillAnalysisParserTest.kt` | +1 (category), plus the category in an existing test |
| `domain/SplitEditsTest.kt` | 17 |
| `domain/SplitEditPropertyTest.kt` | 2 |
| `llm/SplitRevisionParserTest.kt` | 9 |
| `llm/SplitRevisionRequestTest.kt` | 4 |
| `domain/InsightsTest.kt` | 6 |
| `domain/RecurrenceTest.kt` | 10 |

**Property tests.** Each runs 1,500 random bills and plans with up to 20 random edits. After every edit:
- a plan without problems has shares that add up to the total and are never negative, and payments that add up too;
- a rejected edit leaves the bill and plan equal to before;
- an edit naming an unknown item, a person who is not on the bill, or a ref no earlier `add_person` used is rejected with a reason.

I measured the generator with a throwaway probe. Every kind of edit is applied between 176 and 1,270 times per run, so the invariants are not checked on rejections alone.

**The sample scenario.** "Lan no longer drinks beer" moves the shares from 84,000 / 84,000 / 84,000 / 52,500 to 99,750 / 99,750 / 52,500 / 52,500, which equals `SplitCalculator`'s result. The earlier `(bill, plan)` still gives the old shares.

**Hostile cases.** Negative parts, percentages over 100%, sums that do not match, and `Long.MAX_VALUE` pairs that would overflow into the total are all rejected, and the state is unchanged.

**The parser.** Besides the well-formed, partly malformed and hostile answers, the parser test has a fuzz over 3,000 mutated answers. Parsing never throws, and whatever it reads can be applied and described.

## Commits

- `c2a7e2c` feat(domain): give bills one of nine spending categories
- `a2244d6` feat(domain): apply the split edits the AI proposes and describe them
- `1885399` test(domain): check the split edit rules on random bills and edit lists
- `570c209` feat(domain): read the split revision answer and build its request
- `5af9889` feat(domain): add spending insights in the local currency
- `5e808cc` feat(domain): add weekly and monthly schedules for recurring bills
- this report

## Open questions

1. **Name checks on the proxy.** Does the proxy check names, labels or the merchant in the split-revision request, for example their length, control characters or blank names? The builder sends them as stored. If the proxy refuses some, the builder should clean them the way `BillProjection` does for links.
2. **A question with edits.** When the model sends both a question and edits, should the app show only the question? The contract says the edits are empty then; the parser keeps both and leaves the choice to the app.
3. **Docs.** `docs/architecture.md` lists the domain's classes. It will need `BillCategory`, `SplitEdit`/`SplitEdits`, `SplitRevisionParser`/`SplitRevisionRequest`, `Insights` and `Recurrence` once the phase lands. That was outside my files.

```text
Status: DONE
Summary: Categories, the thirteen split edits with their parser, apply, describe and request builder, insights and recurrence are built in :core:domain with 51 new tests; domain tests, ktlint, the app compile and the app tests all pass.
Concerns/Blockers: none; the open questions above are for the proxy and app work.
```
