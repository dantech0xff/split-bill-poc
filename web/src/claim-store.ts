import { CLAIM_KEY, isObject } from "./contract";

/** What a browser keeps of its claim on one link: the secret key the proxy handed out, the claim's id and the name typed. */
export interface ClaimRecord {
  key: string;
  claimId: string;
  name: string;
}

export interface ClaimStore {
  /** False when the browser keeps nothing past this page, so picks last only while it stays open. */
  readonly persistent: boolean;
  load(): ClaimRecord | null;
  save(record: ClaimRecord): void;
  clear(): void;
}

const STORAGE_PREFIX = "sv.claim.";

/**
 * The storage key of the claim on the link of [token]: a SHA-256 of the token, so the token itself
 * is never written down. The text hashed starts with a label of its own, so the key never equals
 * the token's hash that the proxy keeps.
 */
export async function claimStorageKey(token: string, subtle: SubtleCrypto): Promise<string> {
  const digest = await subtle.digest("SHA-256", new TextEncoder().encode(`split-vibe claim key\u0000${token}`));
  const hex = Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
  return `${STORAGE_PREFIX}${hex}`;
}

function parseRecord(text: string | null): ClaimRecord | null {
  if (text === null) return null;
  try {
    const value: unknown = JSON.parse(text);
    if (!isObject(value)) return null;
    const { key, claimId, name } = value;
    if (typeof key !== "string" || !CLAIM_KEY.test(key)) return null;
    if (typeof claimId !== "string" || claimId.length === 0 || claimId.length > 64) return null;
    if (typeof name !== "string" || name.length > 200) return null;
    return { key, claimId, name };
  } catch {
    return null;
  }
}

/** A store that keeps the claim in this page's memory only. */
export class MemoryClaimStore implements ClaimStore {
  readonly persistent = false;
  private record: ClaimRecord | null;

  constructor(record: ClaimRecord | null = null) {
    this.record = record;
  }

  load(): ClaimRecord | null {
    return this.record;
  }

  save(record: ClaimRecord): void {
    this.record = { ...record };
  }

  clear(): void {
    this.record = null;
  }
}

/**
 * A store in localStorage under [storageKey]. The first write that fails, because the browser
 * refuses storage or it is full, moves the claim to memory for the rest of the page's life, and
 * [persistent] turns false so the page can say so.
 */
export class LocalClaimStore implements ClaimStore {
  private fallback: MemoryClaimStore | null = null;

  constructor(
    private readonly storage: Storage,
    private readonly storageKey: string,
  ) {}

  get persistent(): boolean {
    return this.fallback === null;
  }

  load(): ClaimRecord | null {
    if (this.fallback) return this.fallback.load();
    try {
      return parseRecord(this.storage.getItem(this.storageKey));
    } catch {
      this.fallback = new MemoryClaimStore();
      return null;
    }
  }

  save(record: ClaimRecord): void {
    if (this.fallback) {
      this.fallback.save(record);
      return;
    }
    try {
      this.storage.setItem(this.storageKey, JSON.stringify(record));
    } catch {
      this.fallback = new MemoryClaimStore(record);
    }
  }

  clear(): void {
    if (this.fallback) {
      this.fallback.clear();
      return;
    }
    try {
      this.storage.removeItem(this.storageKey);
    } catch {
      this.fallback = new MemoryClaimStore();
    }
  }
}

/** The page's localStorage when it can be read and written; null when the browser refuses it. */
export function usableLocalStorage(view: { readonly localStorage: Storage } | undefined = globalThis.window): Storage | null {
  try {
    const storage = view?.localStorage;
    if (!storage) return null;
    const probe = `${STORAGE_PREFIX}probe`;
    storage.setItem(probe, "1");
    storage.removeItem(probe);
    return storage;
  } catch {
    return null;
  }
}

/**
 * The store for the claim on the link of [token]: localStorage when the browser allows it, and
 * memory otherwise, also when it cannot hash, as outside a secure context.
 */
export async function openClaimStore(
  token: string,
  storage: Storage | null = usableLocalStorage(),
  subtle: SubtleCrypto | null = globalThis.crypto?.subtle ?? null,
): Promise<ClaimStore> {
  if (storage === null || subtle === null) return new MemoryClaimStore();
  try {
    return new LocalClaimStore(storage, await claimStorageKey(token, subtle));
  } catch {
    return new MemoryClaimStore();
  }
}
