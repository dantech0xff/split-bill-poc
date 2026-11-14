# Code review: Phase 3 app side (proxy client and Google sign-in)

- Branch: `feat/phase-03-cloudflare-ai-proxy`, app commits `7e15b75` and `5c1dba4`
- Reviewer: independent code reviewer (read-only; ran the touched unit tests; checked library bytecode where it mattered)
- Date: 2026-11-14
- The reviewer returned this report as text; the controller session saved it here, shortened where it repeats, and added "What was done" at the end.

## Verdict

The proxy client is well built and follows the contract: every code the Worker sends maps to a kind, the stream reader skips wait, blank and unknown lines and treats an early end as a network failure, usage is recorded from every reply that carries it, the timeouts fit the wait lines and the model deadline, and the session and account address are encrypted with a Keystore key. Not to merge yet: the request id was not kept when a read was cancelled or when the app's own parser rejected a charged answer, so Cancel could cost a trial user two of three reads for one bill (High), and an expired session was deleted on the phone and the read sent as the install, which the contract's "401 session" rule exists to prevent (Medium). Seven Low findings followed.

## Findings

| # | Severity | Where | Finding |
|---|---|---|---|
| 1 | High | `llm/GatewayBillAnalyzer.kt` | The request id was remembered only for proxy and IO failures. A cancelled read (Cancel, or back while working) and a charged answer the app's parser rejected both made the next read of the same bill a new request, charged again. |
| 2 | Medium | `data/account/AccountStore.kt`, `data/proxy/ProxyApi.kt`, `GatewayBillAnalyzer.kt` | An expired session was deleted and the read sent as the install, and a 401 `session` also cleared the session, so a signed-in person was moved onto the trial without knowing. |
| 3 | Low | `ui/settings/SettingsViewModel.kt` | The quiet refresh started when Settings opens could end a running action's "working" state and write the install's count over the account's after a sign-in. |
| 4 | Low | `data/settings/KeystoreCipher.kt` | `decrypt` caught only two exception types; a Keystore `ProviderException` at start would crash the app for a signed-in person on every launch. |
| 5 | Low | `GatewayBillAnalyzer.kt` | A 400 `input` kept the id, so after a change to the prompt template a retry of that bill was refused for a day; `retry_limit` advised waiting although a retry with a new id works at once. |
| 6 | Low | `ui/settings/SettingsScreen.kt` | The outcome of an account action was not announced to TalkBack, and deleting the account gave no confirmation. |
| 7 | Low | `ProxyApi.kt` | Fields read with `.jsonPrimitive` threw for JSON of the wrong shape, which surfaced as "Something went wrong" and lost the id. |
| 8 | Low | `app/build.gradle.kts` | The release check accepted an address with an unfilled placeholder, and it ran for every task, so a plain-http local address broke debug builds and unit tests. |
| 9 | Low | several | Messages that did not fit: a connect timeout reported as a slow read; an instruction or bill text over the proxy's limits told to "try another photo"; sign-in advised in a build that cannot sign in; no Google Play services reported as a retryable failure; the AI-off notice repeated in an error card below it. |

Test gaps the reviewer listed: no test cancels a read or rejects a charged answer; the id-reuse test covered only a 500 before the stream; the expiry tests asserted the forbidden switch to the install; the on-device test did not exercise decryption; nothing checked that the account address is stored encrypted; no tests for the Settings view model or for when "Open settings" appears; the refusal table lacked several codes and wrong-shaped JSON; the production client's timeouts were untested.

Leftovers that cause no failure: stale anthropic-java entries in `gradle/verification-metadata.xml`, a mention of the Anthropic SDK in `app/proguard-rules.pro`, an unused `CallsLeft.isTrial`, a comment about `MutableContextWrapper` that claims more than it does, and the plan's criterion "switching AI off moves the app to manual entry", which needs Phase 4's manual entry.

## What was done

All nine findings were fixed, each with a test that fails without the fix. For the three that matter most (the request id, the expired sign-in and the refresh), the fix was taken out again and the new tests failed.

1. **High.** `GatewayBillAnalyzer` now remembers the request id before the request goes out. It forgets the id only once the app has parsed a usable answer, or when the proxy says the id cannot work again (`retry_limit`, `input`). A cancelled read, a cut-off answer, a charged error line and an answer the parser rejects therefore all keep the id. Tests cancel a read while the model works, run the four failures in one test, and check that `retry_limit` and `input` get a new id.
2. **Medium.** A session that runs out or that the proxy refuses is no longer deleted. It becomes a sign-in that has run out (`Account.signInExpired`), kept in preferences, so the next start of the app still knows. A session the phone can no longer decrypt counts the same way. While it lasts, `ProxyApi` refuses requests on the phone the way the proxy refuses an expired session, so nothing goes out as the install. Signing in again or signing out ends it, and signing in sends no session. Settings says "Your sign-in as … has run out" and offers "Sign in with Google" and "Sign out", without "Delete account", which needs a valid session. The refresh treats a refused session as this state rather than as an error.
3. **Low.** An account action stops the quiet refresh that opening Settings starts. Only the action ends its own progress.
4. **Low.** `KeystoreCipher.decrypt` returns null for any failure, including a Keystore `ProviderException`.
5. **Low.** `input` and `retry_limit` drop the id. `retry_limit` now says the read "did not work after several tries" and to try again.
6. **Low.** The account line, the working row and the message card in Settings are polite live regions. Deleting the account confirms "Your account was deleted.", shown as a notice rather than an error.
7. **Low.** `ProxyApi` reads every field through accessors that treat a wrong shape as missing, so wrong-shaped JSON is a bad response.
8. **Low.** Every build parses the proxy address: the scheme must be http or https, there must be a host, and a `<placeholder>` fails. The https rule runs only when a release is built, as the first action of its `pre…ReleaseBuild` task. The web client ID must also look like one. Checked by hand with a temporary `local.properties`: a plain-http address of a computer on the local network compiles a debug build and stops a release build, and a placeholder stops every build.
9. **Low.** The messages now fit the case:
   - A failure before any answer is a network failure; silence after the answer started is a timeout (`AnswerTimeoutException`).
   - The instruction field holds at most 1,000 characters. A bill text over 20,000 characters is refused on the phone as too long.
   - The analyzer and the split screen know whether the build can sign in.
   - A phone without Google Play services hears that signing in with Google is not available on it.
   - An error equal to the AI notice is not shown a second time.

Test gaps closed: cancelling a read and rejecting a charged answer; the id after a cut stream and after a charged error line; expiry and refusal kept as a state across restarts; the on-device test now decrypts the session and the address in a second store; the address is checked to be stored encrypted; the Settings view model has its own tests; "Open settings" is tested for each case; and the refusal table now covers `not_configured`, `install`, `internal`, `retry_limit` and wrong-shaped JSON. A further test checks that the production client's timeouts suit the proxy's wait lines and its model deadline.

Leftovers removed: the three `com.anthropic` components in `gradle/verification-metadata.xml`, the Anthropic mention in `app/proguard-rules.pro` and the unused `CallsLeft.isTrial`. `MutableContextWrapper` did nothing, so it is gone with its comment. The plan's criterion now says that switching AI off shows the AI-off notice, and that manual entry comes with Phase 4.

New screenshots: `17-settings-sign-in-expired` in light and dark.
