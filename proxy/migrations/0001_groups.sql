-- Shared data: groups, their members, bills and settlements, payment requests and the devices
-- that get push notifications. Every permission is checked by the Worker; the schema only keeps
-- rows linked and shaped as the Worker writes them. Accounts are the salted hashes of Google ids
-- that sessions carry; no email, Google name or photo is ever stored. Times are epoch ms.

CREATE TABLE groups (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  currency_code TEXT NOT NULL,
  currency_digits INTEGER NOT NULL CHECK (currency_digits BETWEEN 0 AND 3),
  -- Always a member with an account: ownership passes on when the owner leaves.
  owner_member_id TEXT NOT NULL,
  revision INTEGER NOT NULL CHECK (revision >= 1),
  created_at INTEGER NOT NULL
);

-- Every write to a group sets its revision to the one it read plus one. When another write came
-- first, the revision has moved on, and the whole batch is refused; the Worker reads the group
-- again and decides anew. So no write is ever based on a state that has changed meanwhile.
CREATE TRIGGER groups_revision_guard
BEFORE UPDATE OF revision ON groups
WHEN NEW.revision <> OLD.revision + 1
BEGIN
  SELECT RAISE(ABORT, 'stale_group');
END;

-- A member without an account is a placeholder. A member's id never changes, so bills and
-- settlements keep pointing at the same person when an account claims, leaves or is deleted.
CREATE TABLE members (
  group_id TEXT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  id TEXT NOT NULL,
  name TEXT NOT NULL,
  account TEXT,
  joined_at INTEGER NOT NULL,
  PRIMARY KEY (group_id, id)
);
CREATE UNIQUE INDEX members_one_per_account ON members (group_id, account) WHERE account IS NOT NULL;
CREATE INDEX members_by_account ON members (account);

-- An account asking to become a placeholder; the owner approves or rejects it. One claim per
-- placeholder, and one per account in a group, at a time.
CREATE TABLE claims (
  group_id TEXT NOT NULL,
  member_id TEXT NOT NULL,
  account TEXT NOT NULL,
  claimant_name TEXT NOT NULL,
  requested_at INTEGER NOT NULL,
  PRIMARY KEY (group_id, member_id),
  FOREIGN KEY (group_id, member_id) REFERENCES members (group_id, id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX claims_one_per_account ON claims (group_id, account);
CREATE INDEX claims_by_account ON claims (account);

-- Only the SHA-256 of an invite's token is kept, so the table cannot be read back into links.
CREATE TABLE invites (
  token_hash TEXT PRIMARY KEY,
  group_id TEXT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER
);
CREATE INDEX invites_by_group ON invites (group_id);
CREATE INDEX invites_by_expiry ON invites (expires_at);

-- A deleted bill keeps its row, emptied, so that its id is never used again in the group.
CREATE TABLE group_bills (
  group_id TEXT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  id TEXT NOT NULL,
  owner_member_id TEXT NOT NULL,
  revision INTEGER NOT NULL CHECK (revision >= 1),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  -- JSON as the app sent it, and its size in bytes; null once the bill is deleted.
  document TEXT,
  document_bytes INTEGER,
  conversion TEXT,
  total_minor INTEGER CHECK (total_minor >= 0),
  -- JSON objects from member id to an amount in the group's minor units.
  shares TEXT,
  paid TEXT,
  deleted_at INTEGER,
  PRIMARY KEY (group_id, id),
  FOREIGN KEY (group_id, owner_member_id) REFERENCES members (group_id, id)
);
-- The bills a group's state holds, in order, without reading the deleted ones.
CREATE INDEX group_bills_live ON group_bills (group_id, created_at, id) WHERE deleted_at IS NULL;

-- Money one member paid another, recorded by the member who received it. Rows are only ever
-- added: no route changes or deletes one, and the triggers below refuse any change, and any
-- deletion but the one that comes with deleting the whole group.
CREATE TABLE settlements (
  group_id TEXT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  id TEXT NOT NULL,
  from_member_id TEXT NOT NULL,
  to_member_id TEXT NOT NULL,
  amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
  recorded_by_member_id TEXT NOT NULL,
  request_id TEXT,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (group_id, id),
  FOREIGN KEY (group_id, from_member_id) REFERENCES members (group_id, id),
  FOREIGN KEY (group_id, to_member_id) REFERENCES members (group_id, id),
  FOREIGN KEY (group_id, recorded_by_member_id) REFERENCES members (group_id, id)
);
CREATE UNIQUE INDEX settlements_one_per_request ON settlements (request_id) WHERE request_id IS NOT NULL;

CREATE TRIGGER settlements_append_only
BEFORE UPDATE ON settlements
BEGIN
  SELECT RAISE(ABORT, 'append_only');
END;

-- A group's deletion removes its row before the cascade reaches its settlements, so only that
-- deletion gets past this.
CREATE TRIGGER settlements_kept
BEFORE DELETE ON settlements
WHEN EXISTS (SELECT 1 FROM groups WHERE id = OLD.group_id)
BEGIN
  SELECT RAISE(ABORT, 'append_only');
END;

-- What happened in a group, for its members to read. Entries name members, bills and
-- settlements by id only.
CREATE TABLE group_log (
  seq INTEGER PRIMARY KEY,
  group_id TEXT NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
  at INTEGER NOT NULL,
  kind TEXT NOT NULL,
  member_id TEXT NOT NULL,
  subject_id TEXT NOT NULL
);
CREATE INDEX group_log_by_group ON group_log (group_id, seq);

-- A payment request from a creditor to one debtor. The debtor is bound once, either at once (a
-- group member with an account) or by accepting the request's link. The creditor and the debtor
-- become null when their account is deleted; the request stays for the other side.
CREATE TABLE requests (
  id TEXT PRIMARY KEY,
  creditor TEXT,
  debtor TEXT,
  debtor_bound_at INTEGER,
  amount_minor INTEGER NOT NULL CHECK (amount_minor > 0),
  currency_code TEXT NOT NULL,
  currency_digits INTEGER NOT NULL CHECK (currency_digits BETWEEN 0 AND 3),
  payer_name TEXT NOT NULL,
  -- The name the creditor wants the payer to see, and what the request is for (null for nothing).
  creditor_name TEXT NOT NULL,
  title TEXT,
  -- The one payment method the creditor chose, as JSON; the list of methods stays on the phone.
  method TEXT NOT NULL,
  group_id TEXT,
  to_member_id TEXT,
  creditor_member_id TEXT,
  bill_id TEXT,
  -- The SHA-256 of the link's token.
  link_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  reported_paid_at INTEGER,
  confirmed_paid_at INTEGER,
  remind_every_days INTEGER NOT NULL CHECK (remind_every_days IN (0, 1, 3, 7)),
  last_reminded_at INTEGER,
  -- A deleted request keeps an emptied row for a while, so that its link answers "gone".
  deleted_at INTEGER
);
CREATE INDEX requests_by_creditor ON requests (creditor, created_at);
CREATE INDEX requests_by_debtor ON requests (debtor, created_at);
CREATE INDEX requests_by_group ON requests (group_id);
CREATE INDEX requests_open_reminders ON requests (created_at)
  WHERE remind_every_days > 0 AND confirmed_paid_at IS NULL AND deleted_at IS NULL;
CREATE INDEX requests_deleted ON requests (deleted_at) WHERE deleted_at IS NOT NULL;

-- The FCM token of each install of the app that a signed-in account registered.
CREATE TABLE devices (
  id TEXT PRIMARY KEY,
  account TEXT NOT NULL,
  token TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX devices_by_account ON devices (account, updated_at);
CREATE INDEX devices_by_token ON devices (token);

-- Fixed one-hour windows that count what an account created, for the hourly limits.
CREATE TABLE rate_windows (
  account TEXT NOT NULL,
  kind TEXT NOT NULL,
  started_at INTEGER NOT NULL,
  used INTEGER NOT NULL,
  PRIMARY KEY (account, kind)
);
CREATE INDEX rate_windows_by_start ON rate_windows (started_at);
