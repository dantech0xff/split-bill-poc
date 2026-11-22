# Release checklist for Split Vibe 1.0

Everything that must be true before Split Vibe 1.0 reaches everyone on Google Play, in the order the
owner does it, with how to check each item. The gates and targets come from
[the Phase 9 plan](../plans/261114-0220-split-vibe-production/phase-09-quality-compliance-release.md).
How a release is built, signed, uploaded and deployed is in [release.md](release.md); the device
tests are described in [testing.md](testing.md); the declarations in [play-console.md](play-console.md);
the legal work in [compliance.md](compliance.md); running the service in [runbook.md](runbook.md).

Record each result, with its date and evidence, in `plans/reports/`. Play's rules quoted here were
checked on 19 November 2026.

## Blocking gates

Each must pass on the build that is released. A failure stops the release.

- [ ] **Tests in CI are green on the release commit.** The unit tests with the screenshot
      comparison, the proxy's tests and the web pages' tests: the checks of `ci.yml` on the commit the
      tag points to, and the same checks that `release.yml` runs again. By hand:
      `./gradlew :core:domain:test :core:designsystem:testDebugUnitTest :app:testProdDebugUnitTest`,
      `npm test` in `web/`, and `npm run typecheck` and `npm test` in `proxy/`.
- [ ] **End-to-end tests pass on an Android 14 (API 34) device:** splitting the sample bill through
      the AI, typing a bill in by hand, and making a payment request, against a fake proxy that runs
      inside the test. They run in `device-tests.yml`; [testing.md](testing.md) says how to run them.
      The real AI path is checked by the evaluation below.
- [ ] **The performance budget of Phase 2 holds on the reference phone,** with the release build and
      its baseline profile, for the three journeys (starting, splitting the sample bill, scrolling
      the result): `frameDurationCpuMs` at most 6 ms at P50 and 12 ms at P95, `frameOverrunMs` not
      positive at P95, and a cold start to the first frame under 1 second. Run
      `./gradlew :baseline-profile:connectedProdBenchmarkReleaseAndroidTest` with the reference phone
      connected. This needs the owner's mid-range reference phone.
- [ ] **The AI evaluations reach their thresholds on the models used in production.** Edits by a
      sentence: the chosen model gives exactly the expected edits for at least 90% of the cases of
      `proxy/eval/revise/` in every run (`npm run eval:revise` with the owner's key; see
      [proxy.md](proxy.md#the-evaluation-of-edits)). Bill reads: Phase 3 asked for an evaluation of at
      least 30 sample bills, but no such set is in the repository and Phase 3 names no numeric
      threshold. The owner must set the threshold and have the set built and run, or decide how the
      bill read is judged, before this gate can pass.
- [ ] **Accessibility:** TalkBack goes through the main flow (new split, result, request) with every
      control named, text at 200% cuts nothing off, and colours keep their contrast. The screenshot
      tests draw the main screens at twice the text size, `ColorTokensTest` checks the palette's
      contrast, and every tap of the device tests runs Android's accessibility checks; TalkBack itself
      is checked by hand on a phone.
- [ ] **The account deletion matrix passes:** `proxy/test/account.test.ts` in `npm test`, including
      that the other members' balances do not change, and then once on production with a test
      account: groups, requests, bill links and devices behave as the plan's matrix says, and the
      RevenueCat customer is gone.
- [ ] **Security review:** the threat model is written and every finding rated high is fixed. The
      release checks pass on the final bundle: not debuggable, no plain HTTP, the proxy address is
      `https`, no string that looks like a secret. Secret scanning and the dependency checks are
      green.

## Targets

Measured and recorded, not blocking:

- [ ] The same end-to-end tests on API 26, 30 and 36, run by hand ([testing.md](testing.md)).
- [ ] The web pages' Lighthouse scores, from Chrome's Lighthouse on a bill link, a payment request
      and an invite page.
- [ ] The web pages' download size: `npm run size` in `web/`, against its 150 KB budget.

## The final bundle

Check these on the signed app bundle that will be uploaded to production.

- [ ] **16 KB pages.** Play requires apps targeting API 35 or higher to support 16 KB memory pages on
      64-bit devices, and from 1 February 2027 updates that do not cannot be released. Check that the
      bundle asks for 16 KB alignment, and that every APK split from it passes:

      ```bash
      bundletool dump config --bundle=app-prod-release.aab | grep alignment
      ```

      `PAGE_ALIGNMENT_16K` is the answer to see. Then build the APKs from the bundle with
      `bundletool build-apks` and check each one:

      ```bash
      zipalign -c -P 16 -v 4 <split>.apk
      ```

      The last line must say "Verification successful". `release.yml` runs this check
      ([release.md](release.md)). On 14 November 2026 all 8 native libraries of the debug APK passed.
- [ ] **Target SDK 36.** Play requires new apps and updates to target Android 16 (API 36) since
      31 August 2026. `targetSdk` is 36 in `app/build.gradle.kts`; confirm it in the bundle, for
      example with `bundletool dump manifest --bundle=app-prod-release.aab | grep -i targetSdk`.
- [ ] **material3 is stable.** The project pins `androidx.compose.material3:material3` to
      `1.5.0-rc01` (`gradle/libs.versions.toml`), and no production release may ship on a release
      candidate. AndroidX's release notes list material3 1.5.0 as released on 18 November 2026, but
      Google Maven did not yet serve `1.5.0` on 19 November 2026, when this was checked. Once it
      does: move the pin to `1.5.0`, regenerate the dependency checksums (`checksums.yml`), and run the
      screenshot tests, recording new images only for a change that is intended.
- [ ] **No advertising ID.** The merged manifest of the bundle has no
      `com.google.android.gms.permission.AD_ID`.
- [ ] **Google Play Billing Library 8 or later.** RevenueCat 11.3.0 brings 9.1.0.

## Before the closed test

- [ ] **Trademark search for "Split Vibe"**, before the app is created in Play Console: the package
      name `com.dantech.splitvibe` cannot change after the first upload. If the name is taken, change
      the display name and the package first.
- [ ] **The proxy is set up for production** as [proxy.md](proxy.md#deploy-it) describes:
      `GOOGLE_CLIENT_ID`, `ANDROID_CERT_SHA256` with the upload key and the Play app signing key,
      the Turnstile keys, `ABUSE_EMAIL`, and the secrets for the AI, push and RevenueCat.
      `ANDROID_DEV_CERT_SHA256` stays empty on production, so that no app signed with a debug key
      opens its links. Add the address of the `/delete-account` page to the OAuth client's
      Authorized JavaScript origins, so that signing in there works.
- [ ] **Spend.** The caps in `proxy/src/policy.ts` fit the expected use, and a monthly spend limit
      is set in the Anthropic console ([runbook.md](runbook.md#spend-caps)).
- [ ] **Workers Paid.** The Worker runs on Cloudflare's Workers Paid plan before launch. On the Free
      plan every request to every path counts against one limit of 100,000 a day, after which the
      whole service fails until midnight UTC, and a `workers.dev` address offers nowhere to put a
      rate-limiting rule in front of it ([security-threat-model.md](security-threat-model.md)).
- [ ] **`ABUSE_EMAIL` is set.** The payment request and bill link pages show what their senders wrote,
      and their "Report abuse" link only appears with an address to write to. `release.yml` refuses a
      tag whose `proxy/wrangler.jsonc` leaves it empty.
- [ ] **The legal pages are reviewed** and their placeholders filled ([compliance.md](compliance.md)),
      and `https://<the proxy's host>/privacy`, `/terms` and `/delete-account` answer, and so does
      the home page, `/`, which the store listing gives as the app's website.

## The owner's steps, in order

1. **Create the app in Play Console** with the package `com.dantech.splitvibe`, once the trademark
   search is done.
2. **Upload the first signed build to internal testing.** `release.yml` uploads to the internal
   track ([release.md](release.md)). With a build uploaded, Play lets the Pro subscription be made:
   set it up as [pro-subscription.md](pro-subscription.md) describes, and test the whole cycle with
   license testers.
3. **Run the closed test.** A personal developer account created after 13 November 2023 needs a
   closed test with at least 12 testers who have been opted in for at least 14 days in a row before
   it can apply for production. A tester who leaves before 14 days does not count, and one who leaves
   and joins again counts only for consecutive days. Recruit about 20 testers from the start, so that
   the count never drops below 12, and give them the opt-in link, a way to send feedback and the
   14-day rule. Keep a record of their feedback and of what changed because of it.
4. **Fill in the App content declarations** while the test runs: the privacy policy, Data safety,
   the content rating, the target audience, ads, app access, the advertising ID, financial features
   and health apps ([play-console.md](play-console.md)).
5. **Prepare the store listing** ([store-listing.md](store-listing.md)): the name, the descriptions,
   the screenshots, the category and the contact email.
6. **Apply for production** from the Dashboard, under "Apply for production". Play asks about the
   closed test (how testers were recruited, whether they used every feature, their feedback), about
   the app (audience, value, expected installs in the first year) and about how it was made ready.
   Review usually takes 7 days or less.
7. **Open testing**, as the plan's order is internal, then closed, then open, then production. Keep
   it running while production access is reviewed.
8. **Release to production in steps of 5%, 20%, 50% and 100%,** moving to the next step only when,
   on the data of this release:
   - at least 99.5% of daily users have no crash, which is a crash rate in Android vitals of at most
     0.5%, stricter than Play's bad behaviour threshold of 1.09% for the user-perceived crash rate;
   - the user-perceived ANR rate is under Play's bad behaviour thresholds: 0.47% over all devices,
     and 8% on any phone model;
   - the AI cost per active user is within the budget (see the note below);
   - no bug has lost anyone's data.

   Play does not raise a rollout's percentage by itself: raise it in the release's Manage rollout >
   Update rollout. Halting a rollout stops new users from getting the release, while those who have
   it keep it; a halted rollout can be resumed. The plan sets no minimum time per step.

   **Play's staged rollouts work only for updates.** Play's help says that staged rollouts "can only
   be used for app updates, not when publishing an app for the first time", and does not say whether
   a first production release after closed and open testing counts as an update. The owner has
   decided: 1.0 is released in every country at once, and the 5, 20, 50 and 100 steps start with the
   first update. If Play Console does offer a rollout percentage for 1.0, the steps can start there.
9. **Watch the release for 7 days at 100%:** a crash-free rate of at least 99.5% and an ANR rate under
   Play's threshold all week, and an average AI cost per active user in the first week within the
   budget. The runbook's daily checks ([runbook.md](runbook.md#reading-the-counters)) cover this.

**The AI budget.** The success criteria ask that the AI cost per active user stay "within the budget
set in Phase 3". Phase 3 sets the spend caps ($3 per caller, $6 per network address and $20 for the
whole proxy in 24 hours) and estimates about 6 cents a bill read with Opus 5.5, but sets no budget
per active user. The owner must set that number before the rollout starts.

## Drills before production

- [ ] **AI off:** switching `AI_ENABLED` off makes the app fall back to typing in by hand at the next
      request ([runbook.md](runbook.md#switching-ai-off)).
- [ ] **Restore:** the D1 restore drill: export, load into a temporary database, compare and delete
      ([runbook.md](runbook.md#the-restore-drill)).
- [ ] **The release workflow:** one run on the development project, and a run without the manual
      approval fails ([release.md](release.md)).

## Sources

- The Phase 9 plan, its "Requirements", "Success Criteria" and "Risks".
- App testing requirements for new personal developer accounts:
  https://support.google.com/googleplay/android-developer/answer/14151465
- Release app updates with staged rollouts:
  https://support.google.com/googleplay/android-developer/answer/6346149
- Target API level requirements for Google Play apps:
  https://support.google.com/googleplay/android-developer/answer/11926878
- Support 16 KB page sizes: https://developer.android.com/guide/practices/page-sizes
- Android vitals and its bad behaviour thresholds: https://developer.android.com/topic/performance/vitals
- Compose Material 3 releases: https://developer.android.com/jetpack/androidx/releases/compose-material3
