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

  minuteMs: 60 * 1000,
  dayMs: 24 * 60 * 60 * 1000,
  /** Requests from one hashed IP, across every install and account behind it. */
  ip: { perMinute: 10, perDay: 100 },
  /** Spend over each fixed 24 hour window: for one caller, for one IP, and for the whole proxy. */
  callerCapMicro: 1_000_000,
  ipCapMicro: 3_000_000,
  globalCapMicro: 20_000_000,
} as const;

export type RateLimit = { perMinute: number; perDay: number };
