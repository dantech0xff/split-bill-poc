import { describe, expect, it } from "vitest";
import { LocalClaimStore, MemoryClaimStore, claimStorageKey, openClaimStore, usableLocalStorage } from "../src/claim-store";

const TOKEN = "AbCdEfGhIjKlMnOpQrStUv";
const RECORD = { key: "KkKkKkKkKkKkKkKkKkKkKk", claimId: "c-minh", name: "Minh" };

/** A Storage that refuses every read and write, as some in-app browsers do. */
class RefusingStorage implements Storage {
  readonly length = 0;
  clear(): void {
    throw new DOMException("Refused", "SecurityError");
  }
  getItem(): string | null {
    throw new DOMException("Refused", "SecurityError");
  }
  key(): string | null {
    return null;
  }
  removeItem(): void {
    throw new DOMException("Refused", "SecurityError");
  }
  setItem(): void {
    throw new DOMException("Refused", "SecurityError");
  }
}

/** A Storage that reads but is full. */
class FullStorage extends RefusingStorage {
  override getItem(): string | null {
    return null;
  }
  override setItem(): void {
    throw new DOMException("Full", "QuotaExceededError");
  }
}

describe("claimStorageKey", () => {
  it("is a SHA-256 of the token, never the token itself", async () => {
    const key = await claimStorageKey(TOKEN, crypto.subtle);
    expect(key).toMatch(/^sv\.claim\.[0-9a-f]{64}$/);
    expect(key).not.toContain(TOKEN);
    expect(await claimStorageKey(TOKEN, crypto.subtle)).toBe(key);
    expect(await claimStorageKey("BbCdEfGhIjKlMnOpQrStUv", crypto.subtle)).not.toBe(key);
  });
});

describe("openClaimStore", () => {
  it("keeps the claim in localStorage under the hashed key", async () => {
    localStorage.clear();
    const store = await openClaimStore(TOKEN, localStorage, crypto.subtle);
    expect(store.persistent).toBe(true);
    store.save(RECORD);
    const key = await claimStorageKey(TOKEN, crypto.subtle);
    expect(JSON.parse(localStorage.getItem(key) ?? "null")).toEqual(RECORD);
    for (let index = 0; index < localStorage.length; index += 1) {
      const name = localStorage.key(index) ?? "";
      expect(name).not.toContain(TOKEN);
      expect(localStorage.getItem(name) ?? "").not.toContain(TOKEN);
    }
    expect((await openClaimStore(TOKEN, localStorage, crypto.subtle)).load()).toEqual(RECORD);
    store.clear();
    expect(localStorage.getItem(key)).toBeNull();
  });

  it("ignores a stored record of the wrong shape", async () => {
    localStorage.clear();
    const key = await claimStorageKey(TOKEN, crypto.subtle);
    localStorage.setItem(key, JSON.stringify({ key: "not a key", claimId: "c", name: "Minh" }));
    expect((await openClaimStore(TOKEN, localStorage, crypto.subtle)).load()).toBeNull();
    localStorage.setItem(key, "{not json");
    expect((await openClaimStore(TOKEN, localStorage, crypto.subtle)).load()).toBeNull();
  });

  it("keeps the claim in memory when the browser refuses storage", async () => {
    expect(usableLocalStorage({ localStorage: new RefusingStorage() })).toBeNull();
    const store = await openClaimStore(TOKEN, usableLocalStorage({ localStorage: new RefusingStorage() }), crypto.subtle);
    expect(store).toBeInstanceOf(MemoryClaimStore);
    expect(store.persistent).toBe(false);
    store.save(RECORD);
    expect(store.load()).toEqual(RECORD);
  });

  it("keeps the claim in memory when localStorage cannot be reached at all", async () => {
    const throwing = {
      get localStorage(): Storage {
        throw new DOMException("Refused", "SecurityError");
      },
    };
    expect(usableLocalStorage(throwing)).toBeNull();
  });

  it("keeps the claim in memory without a way to hash, outside a secure context", async () => {
    const store = await openClaimStore(TOKEN, localStorage, null);
    expect(store.persistent).toBe(false);
  });

  it("moves to memory when a write fails, and says so", () => {
    const store = new LocalClaimStore(new FullStorage(), "sv.claim.x");
    expect(store.persistent).toBe(true);
    store.save(RECORD);
    expect(store.persistent).toBe(false);
    expect(store.load()).toEqual(RECORD);
    store.clear();
    expect(store.load()).toBeNull();
  });
});
