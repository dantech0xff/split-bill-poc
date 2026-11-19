# Privacy and legal compliance: what remains before release

What the owner and a legal adviser still have to decide, sign or file before Split Vibe 1.0 goes to
production, as of 19 November 2026. These are working notes, not legal advice: every point below
needs the adviser's confirmation. Facts about the law and the providers' terms were checked on
19 November 2026 against the sources listed at the end; what could not be checked is marked
**unverified**.

## The two drafts

[privacy-policy.md](privacy-policy.md) and [terms.md](terms.md) are **drafts for legal review**.
The web app's build (`web/scripts/static-pages.mjs`) renders them into the public pages `/privacy`
and `/terms`, so they are plain Markdown, without front matter. Each starts with its "draft for
legal review" note as an HTML comment, which that build leaves out of the page and GitHub does not
show; this page carries the note too. Do not ship a build of the Worker with them until the adviser
has reviewed them and every placeholder is filled in, and remove the comment then.

### Placeholders

Placeholders are written in square brackets, such as `[owner name]`, rather than angle brackets: in
a Markdown paragraph `<owner name>` reads as an HTML tag, which GitHub hides when it shows the file.

| Placeholder | In | Fill with |
|---|---|---|
| `[effective date]` | both | The date the version takes effect |
| `[owner name]` | both | The person or company that provides Split Vibe and is its data controller |
| `[postal address]` | both | Their postal address |
| `[contact email]` | both | The address for privacy requests, support and disputes |
| `[support channel]` | both | Where users get help (see [runbook.md](runbook.md#support)) |
| `[EU representative name and address]` | privacy policy | The representative under GDPR Article 27, [below](#eu-representative-gdpr-article-27); delete the line if none is needed |
| `[UK representative name and address]` | privacy policy | The representative under UK GDPR Article 27; delete the line if none is needed |
| `[Confirm the transfer safeguard in Anthropic's data processing addendum.]` | privacy policy | The mechanism Anthropic's DPA uses for transfers out of Europe, once read |
| `[governing law]`, `[courts]` | terms | The law and courts the adviser chooses |
| `[Decide whether we refund the unused part of a paid period in that case, and how.]` | terms | The owner's decision when the service or an account ends while Pro is paid |

The drafts link each other as `privacy-policy.md` and `terms.md`, which the build turns into
`/privacy` and `/terms`, so the links work on GitHub and on the pages alike. The link to
`/delete-account` works on the Worker's pages only.

### What the drafts assume

These are choices the drafts make. Each needs the owner's or the adviser's decision:

- **Users are adults, 18 and over.** The privacy policy, the terms and the Play target audience
  ([play-console.md](play-console.md#target-audience-and-content)) all say so. Change all three
  together if the age changes.
- **Legal bases.** The policy names contract (the features asked for), legitimate interests (limits,
  spend caps, Turnstile, logs, traces, aggregate counts) and permission (notifications, the phone's
  speech service). Vietnam's law asks for consent before collection unless an exception applies (see
  [below](#vietnam-cross-border-transfer-and-impact-assessments)). The app asks for consent before
  its first call to the AI, on a consent screen whose wording the adviser is to review, and keeps
  the answer on the phone, sending it nowhere ([architecture.md](architecture.md#the-consent-to-the-ai)).
- **Retention without an end.** Two kinds of server data are kept for as long as the service runs,
  and the policy says so: the trial count of each install, under its random ID, and payment requests
  confirmed as paid, which stay as the other person's record also after an account deletion, with
  the payment method they carried. A request still open is kept until its creditor deletes or
  confirms it. The adviser should decide whether these need a limit; a limit is a change to the
  proxy.
- **Requests about server data.** The server keeps no email or name, so an email does not identify
  an account. Users can see their groups, requests and links in the app and delete everything
  themselves, but there is no export of server data and no way for the owner to find an account
  other than its own sign-in. The policy says the owner may ask a user to show that an account is
  theirs. The adviser should decide whether access and portability (GDPR Articles 15 and 20) need a
  tool.
- **Liability.** The terms cap liability at what the user paid for Pro in 12 months, and carve out
  what consumer law forbids limiting. The adviser should check this, and the rest of the terms,
  against consumer law in the markets the app ships to.
- **Changes.** The drafts promise to publish a new version before it takes effect. The app has no
  way to tell users of a change; the adviser may require one.

### Facts the drafts state

The drafts describe the system as it is after this phase. When the system changes, the policy must
change with it. The statements and their sources:

| Statement | Source |
|---|---|
| Photos never leave the phone; bill text, instruction and local currency go to Anthropic | [proxy.md](proxy.md#what-it-does), `GatewayBillAnalyzer` |
| An edit sends the bill, the names on the split and the sentence | [proxy.md](proxy.md#editing-a-split-by-a-sentence) |
| Accounts are salted hashes of the Google `sub`; no email, name or photo is stored | `proxy/src/session.ts`, [proxy.md](proxy.md#what-it-keeps) |
| Calls: 3 per install, 10 a month, 300 with Pro; limits and spend caps | `proxy/src/policy.ts` |
| What D1 keeps and what the daily run deletes | `proxy/migrations/`, `proxy/src/reminders.ts` |
| What deleting an account deletes and keeps | [proxy.md](proxy.md#deleting-an-account) and the deletion matrix of the Phase 9 plan |
| Logs and traces | `proxy/wrangler.jsonc`, [proxy.md](proxy.md#what-it-keeps) |
| Aggregate counts in Workers Analytics Engine, with no account, install ID or IP | The Phase 9 plan, section "Số đếm trên server" |
| D1 in Cloudflare's ENAM region; Durable Objects near their first use | Owner's account settings, as recorded for this phase |
| Android backup off; permissions | `app/src/main/AndroidManifest.xml` |
| Anthropic deletes API inputs and outputs within 30 days, up to 2 years when flagged | Anthropic's privacy center, updated 1 July 2026 |
| Workers Logs kept 3 days (Free) or 7 days (Paid); traces 7 days; Analytics Engine 3 months; D1 Time Travel 7 days (Free) or 30 days (Paid) | Cloudflare's documentation |
| What ML Kit sends Google | Google's ML Kit data disclosure page |

### Review checklist for the adviser

- [ ] Both drafts read against the system, with the owner, using the table above.
- [ ] Legal bases, the age, the retention points and the request handling above decided.
- [ ] Consumer law: the subscription terms, the cancellation and refund wording, the liability cap
      and the governing law, for each market at launch.
- [ ] Google Play's User Data policy: the policy names the developer and a contact, the data
      collected and shared, secure handling, and retention and deletion, and is labelled a privacy
      policy; it is linked in the app (Settings and the Pro screen) and in Play Console.
- [ ] Anthropic's Commercial Terms, section D.3: users are told that the AI's output may be wrong and
      must be checked. The terms do this in "AI results may be wrong".
- [ ] The Data safety answers in [play-console.md](play-console.md#data-safety) match the policy.
- [ ] US state privacy laws. The earlier research found that California's CCPA applies only above
      thresholds (annual revenue above USD 26,625,000, 100,000 or more California consumers, or half
      of revenue from selling or sharing personal data) that Split Vibe is unlikely to meet at launch;
      the adviser should confirm this and look at other states.

## EU representative (GDPR Article 27)

The GDPR applies to a controller outside the EU that offers services to people in the EU (Article
3(2)). Such a controller "shall designate in writing a representative in the Union" (Article 27(1)),
unless its processing "is occasional", does not include large-scale processing of special categories
of data or of criminal data, "and is unlikely to result in a risk to the rights and freedoms of
natural persons" (Article 27(2)(a)). The representative must be established in a Member State where
the people whose data is processed are (Article 27(3)).

Split Vibe processes the data of its users continuously, not occasionally, so the exception is
unlikely to apply. The adviser should confirm, and the owner should then appoint a representative,
usually a paid service, before the app is available in the EU, and put its name and address in the
privacy policy (Article 13(1)(a) asks for the representative's identity and contact details).

If no representative is appointed by the release date, the plan's fallback is the owner's decision:
delay the release, or leave the EU out of the countries in Play Console until it is in place.

**UK.** The UK GDPR has the same rule in its own Article 27, according to law firms' and service
providers' summaries; the ICO's own page was not read (**unverified**). **Switzerland** was not
checked (**unverified**).

## Vietnam: cross-border transfer and impact assessments

Checked against the official text of the **Law on Personal Data Protection No. 91/2025/QH15**, as
published in the Official Gazette (Công báo No. 971 + 972 of 24 July 2025), and against law firms'
summaries of **Decree 356/2025/ND-CP**. The decree's own text was not read.

What the law says (official text):

- It applies to Vietnamese agencies, organisations and individuals, and to foreign ones that take
  part in, or are involved in, processing the personal data of Vietnamese citizens (Article 1(2)).
- Cross-border transfer includes moving personal data stored in Vietnam to storage outside Vietnam,
  transferring it to an organisation or individual abroad, and using a platform outside Vietnam to
  process personal data collected in Vietnam (Article 20(1)). Split Vibe stores and processes its
  users' data on Cloudflare, Anthropic, Google and RevenueCat, all outside Vietnam.
- Whoever transfers personal data across the border must prepare a **cross-border transfer impact
  assessment dossier** and send one original to the specialised personal data protection agency
  within **60 days of the first transfer** (Article 20(2)). The assessment is done once for the
  whole time of operation and updated as Article 22 says (Article 20(3)). The exemptions in the law
  are state agencies, organisations storing their employees' data in the cloud, a person
  transferring their own data, and other cases the Government sets (Article 20(6)).
- The controller must also prepare a **personal data processing impact assessment dossier** and send
  one original to the same agency within **60 days of starting the processing** (Article 21(1)).
- Both dossiers are updated every 6 months when something changed, and at once in some cases, on the
  National Portal on Personal Data Protection or at the agency (Article 22).
- A violation that may harm national security or order, or a person's life, health, honour, dignity
  or property, must be reported to the agency **within 72 hours** of finding it (Article 23(1)).
  The runbook's incident steps point here.
- Personal data is collected with the data subject's consent before collection, unless the law
  provides otherwise (Article 11(1)). Consent must be voluntary and informed (Article 9); processing
  without consent is allowed in listed cases, among them carrying out an agreement between the data
  subject and the controller (Article 19(1)(d)).
- Fines: up to 5% of the previous year's revenue for an organisation that breaks the rules on
  cross-border transfer, up to VND 3 billion for other violations, and half of those amounts for an
  individual (Article 8(4) to (6)).
- The Ministry of Public Security leads state management of personal data protection (Article
  36(2)).
- **Exemptions by size** (Article 38): for 5 years from 1 January 2026, small enterprises and
  start-ups may choose not to apply Articles 21 and 22 and Article 33(2); business households and
  micro enterprises need not apply them at all. Neither applies to those that provide personal data
  processing services, directly process sensitive personal data, or process the personal data of a
  large number of data subjects. **Article 38 does not name Article 20**, the cross-border transfer
  assessment.

What the decree adds, according to law firms (secondary sources):

- More cases that need no cross-border transfer assessment: journalism, data already disclosed
  lawfully, emergencies, cross-border management of personnel, and providing data across borders to
  conclude a contract or for cross-border transport, logistics, remittance, payment, accommodation,
  visa or scholarship procedures (Frasers, January 2026).
- The size exemptions cover the processing impact assessment and the data protection officer
  (Frasers; Tilleke & Gibbins, 5 January 2026); EY (3 March 2026) says they also cover the
  cross-border transfer assessment. **The sources disagree**, and Article 38 of the law names only
  Articles 21, 22 and 33(2).
- "A large number of data subjects" is 100,000 or more, counted cumulatively (Frasers, EY).
- The list of sensitive personal data includes bank account and bank card information and financial
  transaction history (EY). Split Vibe's server keeps the payment method a request carries, which can
  be a bank account number, so the size exemptions would likely not apply to it in any case.
- EY reports that the Department of Cybersecurity and High-Tech Crime Prevention (A05) of the
  Ministry of Public Security reviews the dossiers within 15 days, with 30 days to complete one, on
  the decree's Forms 09 (processing) and 10 (cross-border transfer).

What the adviser must settle:

- [ ] Whether the owner, as the controller, must file the cross-border transfer dossier, and the
      processing dossier, and when: the 60 days run from the first transfer, which may be the
      closed test, not the production release.
- [ ] Whether any exemption applies: the owner's legal form (individual, household business or
      enterprise), the size exemptions, and the decree's contract case.
- [ ] Whether accepting the terms is enough (Article 19(1)(d)) for what the app sends besides the AI
      calls, and whether the consent screen before the first AI call is enough as it is: it keeps
      the answer on the phone and sends it nowhere, so the owner holds no record of it.
- [ ] Whether the payment methods on requests are sensitive personal data under the decree's list.

**Unverified:** the decree's official text, its forms and procedure (including whether A05 must
approve a dossier before transfers start), the sanctions decree that EY's site mentions, and how an
individual developer is classified.

## Data processing agreements

Each provider's terms were read on 19 November 2026. The owner must make sure each applies to the
account that runs Split Vibe.

- **Anthropic.** Its Commercial Terms (effective 17 June 2025) incorporate its Data Processing
  Addendum by reference (section C), forbid training models on customer content (section B), and
  require the customer to tell its users that the output may be wrong (section D.3). To do: confirm
  the API key belongs to an organisation on the Commercial Terms; read the DPA itself for its
  transfer mechanism and sub-processors (**unverified**), and fill the placeholder in the policy.
  If the adviser wants no 30-day retention of bill text, ask Anthropic about zero data retention.
- **Cloudflare.** Its Self-Serve Subscription Agreement (last updated 12 September 2025) incorporates
  the Customer DPA by reference when customer content includes the personal data of European data
  subjects (section 6.1). The DPA (version 6.4, effective 3 April 2026) makes Cloudflare a
  processor and includes the EU standard contractual clauses, the UK addendum, the Swiss changes and
  the EU-U.S. Data Privacy Framework; sub-processors are listed at
  https://www.cloudflare.com/gdpr/subprocessors/. To do: confirm the account's agreement, and ask
  the adviser whether data of people outside Europe, such as in Vietnam, is covered. Turnstile:
  Cloudflare is the processor of its signals for bot detection and a controller of them for
  improving that detection (Turnstile addendum, 18 June 2025).
- **Google.** The Firebase Data Processing and Security Terms (last modified 21 August 2024) make
  Google a processor where European data protection law applies, with standard contractual clauses
  and the Data Privacy Framework; they take effect when the customer accepts them. To do: accept them
  for the Firebase project that sends push notices. Google Play's distribution and billing, Sign in
  with Google and ML Kit come under their own terms, which were not read (**unverified**); the
  adviser should confirm Google's role for each (processor or independent controller). Set the
  privacy policy and terms addresses on the OAuth client's consent screen as well (**unverified**
  where in Google Auth Platform).
- **RevenueCat.** Its DPA (effective August 2026) forms part of its Terms of Use and customer
  agreements, makes RevenueCat a processor, and offers the standard contractual clauses with the UK
  and Swiss provisions; its sub-processors are in Annex 3. The DPA does not mention the Data Privacy
  Framework. To do: confirm it applies to the project.
- **Frankfurter** receives nothing about users and needs no agreement.

## Sources

Checked on 19 November 2026:

- Law No. 91/2025/QH15, Official Gazette No. 971 + 972, 24 July 2025:
  https://congbaocdn.chinhphu.vn/CongBaoCP/VanBan/2025/6/45578/57730-1-2025971-97291-2025-qh15.pdf
- Frasers Law Company, "The Next Chapter in Data Protection: New Decree Guiding the Personal Data
  Protection Law", January 2026:
  https://www.frasersvn.com/api/uploads/Legal_update_EN_The_Next_Chapter_in_Data_Protection_January_2026_00274356x_B66_F8_c42ac8f381.PDF
- Tilleke & Gibbins, "New Decree Provides Guidance for Vietnam's Personal Data Protection Law",
  5 January 2026: https://www.tilleke.com/insights/new-decree-provides-guidance-for-vietnams-personal-data-protection-law/
- EY Vietnam, legal alert on Decree 356/2025/ND-CP, 3 March 2026:
  https://www.ey.com/vi_vn/technical/tax/tax-and-law-updates/nghi-dinh-so-356-2025-nd-cp-quy-dinh-chi-tiet-mot-so-dieu-va-bien-phap-thi-hanh-luat-bao-ve-du-lieu-ca-nhan
- GDPR Article 27: https://gdpr-info.eu/art-27-gdpr/
- Anthropic Commercial Terms: https://www.anthropic.com/legal/commercial-terms
- Anthropic, how long API data is kept (updated 1 July 2026):
  https://privacy.claude.com/en/articles/7996866-how-long-do-you-store-my-organization-s-data
- Cloudflare Self-Serve Subscription Agreement: https://www.cloudflare.com/terms/
- Cloudflare Customer DPA: https://www.cloudflare.com/cloudflare-customer-dpa/
- Cloudflare Turnstile privacy addendum: https://www.cloudflare.com/turnstile-privacy-policy/
- Firebase Data Processing and Security Terms: https://firebase.google.com/terms/data-processing-terms
- RevenueCat DPA: https://www.revenuecat.com/dpa/
- ML Kit data disclosure: https://developers.google.com/ml-kit/android-data-disclosure
- Google Play's User Data policy: https://support.google.com/googleplay/android-developer/answer/10144311
- The privacy checklist in
  [the research of 14 November 2026](../plans/reports/researcher-261114-0209-product-backend-and-payment-links.md),
  section 5, which this page updates.
