import { type ApiError, type Fetch, claimShare, openShare } from "./api";
import { type ClaimStore, MemoryClaimStore, openClaimStore } from "./claim-store";
import type { PublicShare } from "./contract";
import { LiveConnection, type LiveEnvironment } from "./live";
import { type PageProblem, pageProblemOf } from "./page-problem";
import { billChanged, claimOf, describeChanges, isNewer } from "./share-state";
import type { HumanCheck } from "./turnstile";

/** Why this browser's picks were not saved. */
export type ClaimProblem =
  | { kind: "name_missing" }
  | { kind: "name_too_long" }
  | { kind: "name_invalid" }
  | { kind: "human_check_failed" }
  | { kind: "turnstile" }
  | { kind: "full" }
  | { kind: "locked" }
  | { kind: "rate_limited"; retryAfterSeconds: number | null }
  | { kind: "not_configured" }
  | { kind: "input" }
  | { kind: "network" }
  | { kind: "unexpected" };

export interface ShareState {
  phase: "loading" | "ready" | "failed";
  problem: PageProblem | null;
  share: PublicShare | null;
  /** The id of this browser's claim on the link, when it has one. */
  you: string | null;
  name: string;
  /** The items this browser asked for while a claim is on its way; null once the proxy holds them. */
  picks: readonly string[] | null;
  saving: boolean;
  /** Waiting for Turnstile to say a person makes the first claim. */
  checking: boolean;
  claimProblem: ClaimProblem | null;
  /** False when picks are kept only while the page stays open. */
  persistent: boolean;
  /** The latest sentence for the live region; the id changes with every announcement. */
  announcement: { id: number; text: string } | null;
  billUpdated: boolean;
  /** The owner took this browser's claim off the link. */
  removedByOwner: boolean;
}

export const MAX_NAME_LENGTH = 40;
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

/** What is wrong with [name] as a claim's name: the proxy takes 1 to 40 characters once trimmed. */
export function nameProblem(name: string): ClaimProblem | null {
  const trimmed = name.trim();
  if (trimmed === "") return { kind: "name_missing" };
  if ([...trimmed].length > MAX_NAME_LENGTH) return { kind: "name_too_long" };
  if (CONTROL.test(trimmed)) return { kind: "name_invalid" };
  return null;
}

export function isNameProblem(problem: ClaimProblem | null): boolean {
  return problem !== null && (problem.kind === "name_missing" || problem.kind === "name_too_long" || problem.kind === "name_invalid");
}

function claimProblemOf(error: ApiError): ClaimProblem {
  switch (error.kind) {
    case "turnstile":
    case "full":
    case "locked":
    case "not_configured":
    case "input":
    case "network":
      return { kind: error.kind };
    case "rate_limited":
      return error;
    default:
      return { kind: "unexpected" };
  }
}

export interface ShareControllerOptions {
  token: string;
  /** Turnstile for the first claim; null when the page has no site key. */
  human: HumanCheck | null;
  /** Where and how to hold the live connection; null to go without one. */
  live: { url: string; env: LiveEnvironment } | null;
  fetch?: Fetch;
  store?: Promise<ClaimStore>;
  now?: () => number;
}

/** How long the page waits for the live connection before it reads the link anyway. */
export const OPEN_FALLBACK_MS = 3_000;
/** How often the page reads the link while the live connection keeps failing. */
export const READ_WHILE_DISCONNECTED_MS = 60_000;

interface Write {
  name: string;
  itemIds: string[];
}

/**
 * Everything the bill link's page does with the proxy: it connects live, reads the link with
 * `open`, keeps the view with the highest revision, and saves this browser's picks one claim at
 * a time, the latest picks winning.
 */
export class ShareController {
  private current: ShareState = {
    phase: "loading",
    problem: null,
    share: null,
    you: null,
    name: "",
    picks: null,
    saving: false,
    checking: false,
    claimProblem: null,
    persistent: true,
    announcement: null,
    billUpdated: false,
    removedByOwner: false,
  };
  private readonly listeners = new Set<(state: ShareState) => void>();
  private store: ClaimStore = new MemoryClaimStore();
  private live: LiveConnection | null = null;
  private started = false;
  private stopped = false;
  private finished = false;
  private refreshing: Promise<void> | null = null;
  private refreshAgain = false;
  private lastReadAt = Number.NEGATIVE_INFINITY;
  private queued: Write | null = null;
  private writing = false;
  private fallbackTimer: ReturnType<typeof setTimeout> | null = null;
  private announcements = 0;
  private readonly now: () => number;

  constructor(private readonly options: ShareControllerOptions) {
    this.now = options.now ?? Date.now;
  }

  get state(): ShareState {
    return this.current;
  }

  /** Calls [listener] with the state now and after every change; returns what stops it. */
  subscribe(listener: (state: ShareState) => void): () => void {
    this.listeners.add(listener);
    listener(this.current);
    return () => this.listeners.delete(listener);
  }

  async start(): Promise<void> {
    if (this.started) return;
    this.started = true;
    this.store = await (this.options.store ?? openClaimStore(this.options.token));
    if (this.stopped) return;
    const record = this.store.load();
    this.update({ persistent: this.store.persistent, name: record?.name ?? "" });
    const live = this.options.live;
    if (live === null) {
      void this.refresh();
      return;
    }
    this.live = new LiveConnection(
      this.options.token,
      live.url,
      {
        onConnected: () => void this.refresh(),
        onDisconnected: (failures) => this.disconnected(failures),
        onShare: (share) => this.accept(share),
        onGone: () => this.finish({ kind: "gone" }),
      },
      live.env,
    );
    this.live.start();
    this.fallbackTimer = setTimeout(() => {
      this.fallbackTimer = null;
      if (this.current.share === null && this.refreshing === null) void this.refresh();
    }, OPEN_FALLBACK_MS);
  }

  stop(): void {
    this.stopped = true;
    this.live?.stop();
    this.clearFallback();
    this.options.human?.dispose();
  }

  /** Tries again after the page could not read the link. */
  retry(): void {
    if (this.finished || this.stopped) return;
    if (this.current.phase === "failed") this.update({ phase: "loading", problem: null });
    void this.refresh();
  }

  setName(name: string): void {
    this.update({ name, claimProblem: isNameProblem(this.current.claimProblem) ? null : this.current.claimProblem });
    this.prepareHumanCheck();
  }

  /** Saves a changed name on this browser's claim; a browser without a claim sends it with its first pick. */
  commitName(): void {
    const share = this.current.share;
    if (share === null || share.status !== "open" || this.finished) return;
    const mine = claimOf(share, this.current.you);
    if (mine === null) return;
    const problem = nameProblem(this.current.name);
    if (problem !== null) {
      this.update({ claimProblem: problem });
      return;
    }
    const name = this.current.name.trim();
    if (name === mine.name && this.queued === null) return;
    this.enqueue({ name, itemIds: [...(this.current.picks ?? mine.itemIds)] });
  }

  /** Picks [itemId] for this browser, or puts it back. */
  toggle(itemId: string): void {
    const share = this.current.share;
    if (share === null || share.status !== "open" || this.finished) return;
    if (!share.bill.items.some((item) => item.id === itemId)) return;
    const problem = nameProblem(this.current.name);
    if (problem !== null) {
      this.update({ claimProblem: problem });
      return;
    }
    const picked = this.currentPicks(share);
    const itemIds = picked.includes(itemId) ? picked.filter((id) => id !== itemId) : [...picked, itemId];
    this.prepareHumanCheck();
    this.enqueue({ name: this.current.name.trim(), itemIds });
  }

  dismissBillUpdate(): void {
    this.update({ billUpdated: false });
  }

  /** The items this browser has picked, as the page shows them. */
  currentPicks(share: PublicShare | null = this.current.share): string[] {
    if (this.current.picks !== null) return [...this.current.picks];
    return share === null ? [] : [...(claimOf(share, this.current.you)?.itemIds ?? [])];
  }

  private update(patch: Partial<ShareState>): void {
    this.current = { ...this.current, ...patch };
    for (const listener of this.listeners) listener(this.current);
  }

  private clearFallback(): void {
    if (this.fallbackTimer !== null) clearTimeout(this.fallbackTimer);
    this.fallbackTimer = null;
  }

  private prepareHumanCheck(): void {
    if (this.current.share?.status === "open" && this.store.load() === null) this.options.human?.prepare();
  }

  private disconnected(failures: number): void {
    // The first failure reads the link at once, to learn whether it is gone; then once a minute.
    if (failures === 1 || this.now() - this.lastReadAt >= READ_WHILE_DISCONNECTED_MS) void this.refresh();
  }

  /** Reads the link with `open`. A read asked for while one is on its way runs again after it. */
  refresh(): Promise<void> {
    if (this.finished || this.stopped) return Promise.resolve();
    if (this.refreshing !== null) {
      this.refreshAgain = true;
      return this.refreshing;
    }
    this.clearFallback();
    const run = async (): Promise<void> => {
      do {
        this.refreshAgain = false;
        await this.readOnce();
      } while (this.refreshAgain && !this.finished && !this.stopped);
    };
    this.refreshing = run().finally(() => {
      this.refreshing = null;
    });
    return this.refreshing;
  }

  private async readOnce(): Promise<void> {
    const sent = this.store.load();
    this.lastReadAt = this.now();
    const result = await openShare(this.options.token, sent?.key ?? null, this.options.fetch);
    if (this.finished || this.stopped) return;
    if (!result.ok) {
      const problem = pageProblemOf(result.error);
      if (problem.kind === "gone" || problem.kind === "not_found") this.finish(problem);
      else if (this.current.share === null) this.update({ phase: "failed", problem });
      return;
    }
    const { share, you } = result.value;
    const record = this.store.load();
    // What `open` says of this browser's claim holds only if no claim changed the key meanwhile.
    if ((record?.key ?? null) === (sent?.key ?? null)) {
      if (you === null && record !== null) this.store.clear();
      else if (you !== null && record !== null && record.claimId !== you) this.store.save({ ...record, claimId: you });
      if (you !== this.current.you) {
        this.update({ you, removedByOwner: you === null && this.current.you !== null });
      }
    }
    this.accept(share);
    const mine = this.current.share === null ? null : claimOf(this.current.share, this.current.you);
    if (mine !== null && this.current.name.trim() === "") this.update({ name: mine.name });
  }

  /** Shows [share] if it is later than the view on screen, and says what others changed. */
  private accept(share: PublicShare): void {
    if (this.finished || this.stopped) return;
    const before = this.current.share;
    if (!isNewer(before, share)) return;
    const patch: Partial<ShareState> = { share, phase: "ready", problem: null };
    if (before !== null) {
      const sentences = describeChanges(before, share, this.current.you);
      if (sentences.length > 0) {
        this.announcements += 1;
        patch.announcement = { id: this.announcements, text: sentences.join(" ") };
      }
      if (billChanged(before.bill, share.bill)) patch.billUpdated = true;
    }
    if (this.current.you !== null && claimOf(share, this.current.you) === null) {
      // The owner took this browser's claim off the link; its key opens nothing any more.
      this.store.clear();
      patch.you = null;
      patch.removedByOwner = true;
    }
    if (this.current.picks !== null) {
      const onBill = new Set(share.bill.items.map((item) => item.id));
      patch.picks = this.current.picks.filter((id) => onBill.has(id));
    }
    this.update(patch);
  }

  /** The link does not exist or no longer works: stop everything and say so. */
  private finish(problem: { kind: "not_found" } | { kind: "gone" }): void {
    if (this.finished) return;
    this.finished = true;
    this.queued = null;
    this.live?.stop();
    this.clearFallback();
    this.options.human?.dispose();
    this.announcements += 1;
    const text = problem.kind === "gone" ? "This link no longer works." : "This link does not exist.";
    this.update({
      phase: "failed",
      problem,
      picks: null,
      saving: false,
      checking: false,
      claimProblem: null,
      announcement: this.current.share === null ? this.current.announcement : { id: this.announcements, text },
    });
  }

  private enqueue(write: Write): void {
    this.queued = write;
    this.update({ picks: write.itemIds, claimProblem: null, removedByOwner: false });
    void this.flush();
  }

  private async flush(): Promise<void> {
    if (this.writing) return;
    this.writing = true;
    this.update({ saving: true });
    try {
      while (this.queued !== null && !this.finished && !this.stopped) {
        const write = this.queued;
        this.queued = null;
        if (!(await this.send(write, true))) {
          this.queued = null;
          break;
        }
      }
    } finally {
      this.writing = false;
      if (!this.finished) this.update({ saving: false, picks: null });
    }
  }

  /** Sends one claim; true once the proxy holds it. */
  private async send(write: Write, mayClaimAfresh: boolean): Promise<boolean> {
    const record = this.store.load();
    let turnstile: string | null = null;
    if (record === null) {
      const human = this.options.human;
      if (human === null) {
        this.update({ claimProblem: { kind: "not_configured" } });
        return false;
      }
      this.update({ checking: true });
      try {
        turnstile = await human.token();
      } catch {
        this.update({ claimProblem: { kind: "human_check_failed" } });
        return false;
      } finally {
        this.update({ checking: false });
      }
      if (this.finished || this.stopped) return false;
    }
    const result = await claimShare(
      { token: this.options.token, key: record?.key ?? null, name: write.name, itemIds: write.itemIds, turnstile },
      this.options.fetch,
    );
    if (turnstile !== null) this.options.human?.consumed();
    if (this.finished || this.stopped) return false;
    if (result.ok) {
      const { share, you, key } = result.value;
      const savedKey = key ?? record?.key ?? null;
      if (savedKey !== null) this.store.save({ key: savedKey, claimId: you, name: write.name });
      this.update({ you, persistent: this.store.persistent, removedByOwner: false });
      this.accept(share);
      return true;
    }
    const error = result.error;
    if (error.kind === "turnstile" && record !== null && mayClaimAfresh) {
      // The proxy no longer knows this browser's key, as when the owner removed its claim.
      this.store.clear();
      this.update({ you: null });
      return this.send(write, false);
    }
    if (error.kind === "gone" || error.kind === "not_found") {
      this.finish(error);
      return false;
    }
    this.update({ claimProblem: claimProblemOf(error) });
    if (error.kind === "locked") void this.refresh();
    return false;
  }
}
