-- Bill links: a bill that one account shows on a web page, where the people at the table claim
-- the items they had. Only what the page shows is kept: the bill as the owner's app projected it,
-- the claims under the names people typed, and what each participant owes once the split is
-- settled. A link's token and a claim's key are kept as their SHA-256 only. Times are epoch ms.

CREATE TABLE shares (
  id TEXT PRIMARY KEY,
  -- The account that made the link, as the salted hash its session carries.
  owner TEXT NOT NULL,
  -- The bill in the owner's app that the link shows; it never changes.
  bill_id TEXT NOT NULL,
  -- The SHA-256 of the link's token.
  token_hash TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL CHECK (status IN ('open', 'locking', 'locked')),
  -- Moves on by one at every change of the link or of its claims.
  revision INTEGER NOT NULL CHECK (revision >= 1),
  -- The revision of the bill in the owner's app; a copy older than the one kept is refused.
  bill_revision INTEGER NOT NULL CHECK (bill_revision >= 0),
  -- The bill as the page shows it, as JSON; null once the link is revoked.
  bill TEXT,
  -- What each participant owes and how to pay it, as JSON; null until the split is settled.
  amounts TEXT,
  created_at INTEGER NOT NULL,
  -- 30 days after it was made; nothing moves it.
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER
);
-- Deleting an account deletes its links; the daily run deletes expired and revoked ones.
CREATE INDEX shares_by_owner ON shares (owner);
CREATE INDEX shares_by_expiry ON shares (expires_at);
CREATE INDEX shares_revoked ON shares (revoked_at) WHERE revoked_at IS NOT NULL;

-- Every change of a link goes in one batch behind an update of its revision that says, in SQL,
-- what the change needs of the link as the batch finds it: a claim needs it open with room left,
-- a lock needs it open, and so on. When that no longer holds, the update sets the revision to 0,
-- which this refuses, and the whole batch is rolled back; the Worker reads the link again and
-- decides anew. So a claim that lands before a lock is in the lock's answer, and one after it is
-- refused.
CREATE TRIGGER shares_change_refused
BEFORE UPDATE OF revision ON shares
WHEN NEW.revision < 1
BEGIN
  SELECT RAISE(ABORT, 'share_refused');
END;

-- The items one browser claimed on a link, under the name its user typed. The browser holds the
-- claim's key, which the proxy made; only its SHA-256 is kept.
CREATE TABLE share_claims (
  share_id TEXT NOT NULL REFERENCES shares (id) ON DELETE CASCADE,
  id TEXT NOT NULL,
  key_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  -- A JSON array of ids of the bill's items; ids the bill no longer has are dropped from it.
  item_ids TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  -- When its browser last wrote it.
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (share_id, id)
);
CREATE UNIQUE INDEX share_claims_by_key ON share_claims (share_id, key_hash);

-- How the payer of a payment request pays it, as the creditor's app built it for the request's web
-- page, as JSON; null when the app sent none. Emptied when the request is deleted, and on every
-- request of an account that is deleted; the web page never shows it once the request is confirmed.
ALTER TABLE requests ADD COLUMN payment TEXT;
