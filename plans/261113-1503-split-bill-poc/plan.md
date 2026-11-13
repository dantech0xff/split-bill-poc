# Split Bill POC

Status: delivered (see "Verification" for what was and was not proven)
Date: 2026-11-13

## Outcome

A proof-of-concept Android app (Kotlin, Jetpack Compose) that mimics a payment app with a list of
in-app contacts and an AI "split bill" flow:

1. The user picks a bill photo and types an instruction ("Split with Minh and Lan. Minh had the beers.").
2. ML Kit reads the text on the device, with the position of every line.
3. The text, the positions and the instruction go to Claude Opus, which returns the bill as structured data.
4. The app shows the bill, lets the user pick people and assign items, and computes each share.

## Decisions confirmed with the owner

| Topic | Decision |
|---|---|
| Split logic | Per-item assignment plus equal split. Charges follow each person's items. |
| Live API test in the build session | Approved, but the platform refused to store an Anthropic key, so the prompt was validated through the session's own Claude access instead (see Verification). |
| Delivery | Public GitHub repository `dantech0xff/split-bill-poc`. |
| Stack | minSdk 26, Material 3, single module, MVVM, ML Kit bundled Latin model, official Anthropic Java SDK, model `claude-opus-5-5`. |
| Privacy | Only OCR text is sent to the model. The photo and the contact list stay on the device. |
| Money | All arithmetic on the device, in integer minor units. |
| Currency | Detected from the bill. A bill is split no finer than its prices are printed and no finer than the currency allows. The app's local currency (a setting) is the fallback when a bill shows none. No conversion between currencies. |
| API key | Entered in Settings (encrypted with the Android Keystore) or preset in `local.properties` for debug builds. |
| Contacts and payments | Seeded mock contacts and mock payment requests. |

## Out of scope

Real payments, backend and authentication, device-contacts sync, production key handling (needs a
server proxy), iOS, localisation of UI strings.

## Acceptance criteria

1. `./gradlew assembleDebug` builds an installable APK.
2. Unit tests pass: shares always add up to the bill total, OCR layout encoding, Claude request and response handling.
3. The Claude step is proven on sample bills.
4. The README covers setup, the API key, the architecture and known limits.

## Phases

| Phase | Content | State |
|---|---|---|
| 1 | Gradle project, toolchain, first build | done |
| 2 | Domain: money, bill reconciliation, split calculator, contact name matching | done |
| 3 | OCR: ML Kit recognizer, image loading, row layout encoder | done |
| 4 | Claude analyzer: prompt, answer schema, SDK client, error handling | done |
| 5 | Compose UI: contacts, new split, result with people and item assignment, confirmation, settings | done |
| 6 | Verification on the JVM, on an emulator and against the real model | done |
| 7 | README, independent review, fixes | done |

## Verification

See the "How it was verified" section of the README for the evidence and for what remains open:
the exact HTTP request has not been sent to the live Anthropic API from this project (run
`LiveClaudeBillAnalyzerTest` with a key), and the screens after analysis have run in the
Robolectric flow test but not yet on a device.
