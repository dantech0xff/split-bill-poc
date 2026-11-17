/**
 * The starting limits of the proxy. Change them here before a deploy when the shape of use or of
 * spend should change. They are separate from the spend cap set in the Anthropic console, which is
 * still worth setting.
 *
 * Windows are fixed: a counter starts over one whole window after it began, not on a sliding
 * clock. Money is counted in microdollars, millionths of a dollar, so that it stays a whole number.
 */
export const POLICY = {
  /** Calls a phone that is not signed in may make, once for each install of the app. */
  trialCalls: 3,
  /** Calls a Google account may make in each calendar month, counted in UTC. */
  monthlyCalls: 10,
  /**
   * Calls a Google account on Pro may make in each calendar month, on the same count as
   * [monthlyCalls]: a plan that changes during a month keeps what the month used.
   */
  proCalls: 300,
  /**
   * How long an account stays on Pro after the time RevenueCat last gave it, unless RevenueCat says
   * otherwise first. It covers a renewal that Play makes hours after the period ended, and a webhook
   * that comes late. A subscription that RevenueCat shows cancelled, refunded or failing to bill once
   * its period is over ends at once.
   */
  proGraceMs: 24 * 60 * 60 * 1000,
  /** Times one account may have the proxy ask RevenueCat for its plan in an hour. */
  planRefreshesPerHour: 30,
  /**
   * What an edit of a split by a sentence takes of the same allowance, in calls; a bill read takes
   * one. Allowances are counted in hundredths of a call, so a weight is a whole number of
   * hundredths from 0.01 to 1; the Worker does not load with any other.
   */
  revisionWeight: 0.2,
  /** A held call that is neither committed nor released within this time is given back. */
  holdMs: 5 * 60 * 1000,
  /** The first run of a request plus two runs again with the same request id. */
  runsPerRequest: 3,
  /** How long a finished request id is remembered, so that running it again is not charged twice. */
  requestMemoryMs: 24 * 60 * 60 * 1000,
  /** How long a session lasts after a Google sign-in before the app must sign in again. */
  sessionMs: 30 * 24 * 60 * 60 * 1000,

  minuteMs: 60 * 1000,
  dayMs: 24 * 60 * 60 * 1000,
  /** Requests from one hashed IP, across every install and account behind it. */
  ip: { perMinute: 10, perDay: 100 },
  /**
   * Spend over each fixed 24 hour window: for one caller, for one IP, and for the whole proxy. A
   * call holds the most it could cost while it runs: a bill read, which a refusal can move to
   * another model, nearly half a dollar at the dearest price with a full answer, though a bill
   * should cost far less; an edit at the price of its own model, half a cent to 1.3 cents for
   * Haiku. So the caller's cap leaves room for a day's worth of real calls plus one hold, and the
   * IP's cap for its 100 requests a day. The global cap must rise as accounts move to Pro, each of
   * which may use [proCalls] a month.
   */
  callerCapMicro: 3_000_000,
  ipCapMicro: 6_000_000,
  globalCapMicro: 20_000_000,

  /** The model that reads bills, and how hard it thinks. The model must have a price in `spend.ts`. */
  model: "claude-opus-5-5",
  effort: "medium",
  /** Room for the model's reasoning plus the JSON answer of a long bill. */
  maxTokens: 16_000,
  /**
   * The model that edits a split by a sentence, how hard it works, and room for its answer. An
   * edit maps one sentence onto a few structured edits of a bill already read and split, so low
   * effort, the fastest and cheapest level, meant for short and simple requests, fits it. Thinking
   * stays adaptive: the model thinks briefly when an instruction needs it (several edits at once,
   * percentages that must add up, a name typed differently, a sentence too vague to act on) and
   * answers at once otherwise. With thinking off, this model tends to write its reasoning into
   * the text the user reads, here the explanation; and Claude Sonnet 5.5 and Claude Opus 5.5,
   * which the evaluation compares it with, refuse thinking off, so the same settings compare the
   * three like for like. Its tokenizer counts about 30% more tokens than older models for the
   * same text: 8,000 leave room for a short think and an answer of 20 edits. The evaluation set
   * in `eval/revise/` measures the choice.
   */
  revisionModel: "claude-haiku-5-5",
  revisionEffort: "low",
  revisionMaxTokens: 8_000,
  /**
   * How long the proxy waits for the model, its one retry included. It stays under the 150 seconds
   * the app waits, so the app hears the outcome rather than timing out first.
   */
  modelDeadlineMs: 120_000,
  /** One retry covers a dropped connection or a brief overload, but only when the API asks for no longer a wait than this. */
  modelRetries: 1,
  maxRetryWaitMs: 10_000,
  /**
   * How long the model may still work after the app hung up. The runtime cuts a request off 30
   * seconds after the caller left; stopping before that leaves time to give back and settle.
   */
  afterHangUpMs: 25_000,
  /** How often a wait line goes out while the model works, so that no network drops a quiet connection. */
  waitLineMs: 10_000,

  /** The largest request body read, in bytes; the text limits below fit well inside it. */
  maxBodyBytes: 256 * 1024,
  maxDocumentChars: 20_000,
  maxInstructionChars: 1_000,
  /**
   * The largest body of an edit of a split, in bytes: ample for a bill of 200 items with its
   * split. It keeps the prompt under the 100K tokens up to which the price of the edit's model in
   * `spend.ts` holds, since no token is shorter than a byte.
   */
  maxRevisionBodyBytes: 64 * 1024,

  // Groups, payment requests and push notifications.
  hourMs: 60 * 60 * 1000,
  /** The largest body of a route on shared data, a bill's aside, which may be [maxBodyBytes]. */
  maxSharedBodyBytes: 16 * 1024,
  /** How long an invite link works after it was made. */
  inviteMs: 14 * 24 * 60 * 60 * 1000,
  /** Invites one account may make in an hour, over every group, and payment requests one creditor may. */
  invitesPerHour: 20,
  requestsPerHour: 30,
  /** The largest amount of money the proxy takes, in minor units: the app's `BillCurrency.MAX_AMOUNT_MINOR`. */
  maxAmountMinor: 1_000_000_000_000_000,
  /** A bill's document, the bill a bill link shows, and a request's payment method, serialized as JSON, in bytes. */
  maxBillDocumentBytes: 64 * 1024,
  maxMethodBytes: 4 * 1024,
  /** What a web page shows of how to pay: a link, a code to scan and the details to pay by hand, in characters. */
  maxPaymentLinkChars: 2_048,
  maxPaymentCodeChars: 512,
  maxPaymentDetailsChars: 1_000,
  /**
   * A group's state is always sent whole, and read whole by every change, and an account's groups
   * are listed whole; these keep both small enough for the CPU time and the D1 reads a request
   * may take. The documents of a group's bills count together.
   */
  maxMembers: 100,
  maxBills: 500,
  maxGroupDocumentBytes: 1024 * 1024,
  maxSettlements: 2_000,
  maxGroupsPerAccount: 100,
  /** Changes one account may make to groups in an hour, over all of them: every route that changes a group counts once. */
  groupWritesPerHour: 200,
  /** The devices an account keeps; registering one more forgets the one updated longest ago. */
  maxDevices: 10,
  /** The newest log entries a group's state carries, and the newest requests a list carries. */
  logEntries: 100,
  listedRequests: 200,
  /**
   * Notifications one daily run may send for reminders. With the token fetch and the two fetches
   * of the exchange rates it stays under the 50 subrequests a Worker on the free plan may make;
   * reminders left over go out the next day. Raise it on the paid plan.
   */
  remindersPerRun: 40,
  /** Invites and deleted requests are forgotten this long after they stopped working; their links then answer 404. */
  forgetAfterMs: 30 * 24 * 60 * 60 * 1000,

  // Bill links and the web pages.
  /** How long a bill link works after it was made; nothing extends it. */
  shareMs: 30 * 24 * 60 * 60 * 1000,
  /** Links one account may make in an hour, and changes it may make to its links in an hour, over all of them. */
  sharesPerHour: 30,
  shareWritesPerHour: 300,
  /**
   * The largest body of a link owner's route: a bill of [maxBillDocumentBytes], and what each of up
   * to 30 participants owes with the longest payment the contract takes.
   */
  maxShareBodyBytes: 512 * 1024,
  /** The largest body of a claim: up to 200 item ids, a name and a Turnstile token of up to 2,048 characters. */
  maxClaimBodyBytes: 32 * 1024,
  /** The claims one link holds, and how long a claim's name may be, in characters. */
  maxClaims: 30,
  maxClaimNameChars: 40,
  /** The live connections one link's page may have open at once. */
  maxLiveConnections: 50,
  /**
   * What one hashed IP may do in an hour without a session: new claims, which also need Turnstile;
   * claim writes, new or not; and reads of bill links, of payment request links and of invites
   * for the web pages, live connections included. Only a token that leads to a link, a request or
   * an invite that works is counted. A whole table can claim from one restaurant Wi-Fi, or from
   * behind one carrier's address: as many new claims as a link holds, and about ten taps for each.
   * Turnstile, the owner's removal of a claim, locking and revoking are what stop one address from
   * filling a link.
   */
  newClaimsPerHour: 30,
  claimWritesPerHour: 300,
  webReadsPerHour: 600,
} as const;

export type RateLimit = { perMinute: number; perDay: number };
