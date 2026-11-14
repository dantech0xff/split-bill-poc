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
   * call holds the most it could cost (nearly half a dollar with the dearest model and a full
   * answer) while it runs, though a bill should cost far less. So the caller's cap leaves room for
   * a day's worth of real calls plus one hold, and the IP's cap for its 100 requests a day.
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
   * How long the proxy waits for the model, its one retry included. It stays under the 150 seconds
   * the app waits, so the app hears the outcome rather than timing out first.
   */
  modelDeadlineMs: 120_000,
  modelRetries: 1,
  /** How often a wait line goes out while the model works, so that no network drops a quiet connection. */
  waitLineMs: 10_000,

  /** The largest request body read, in bytes; the text limits below fit well inside it. */
  maxBodyBytes: 256 * 1024,
  maxDocumentChars: 20_000,
  maxInstructionChars: 1_000,
} as const;

export type RateLimit = { perMinute: number; perDay: number };
