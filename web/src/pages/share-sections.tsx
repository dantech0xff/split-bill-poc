import type { RefObject } from "preact";
import { useEffect, useRef } from "preact/hooks";
import { type BillItem, type BillProjection, type Currency, OWNER_PARTICIPANT_ID, type Participant, type PublicShare } from "../contract";
import { formatBillDate } from "../dates";
import { formatMoney } from "../money";
import { type ClaimProblem, MAX_NAME_LENGTH, type ShareState, isNameProblem } from "../share-controller";
import {
  capitalized,
  claimersOf,
  listOf,
  matchOf,
  ownerInSentence,
  participantLabel,
  payerOf,
  personInSentence,
  statusSentence,
  UNNAMED_OWNER,
} from "../share-state";
import { HeroAmount } from "../ui/hero-amount";
import { AlertIcon, CheckIcon, ExpandIcon, LockIcon, NoticeIcon } from "../ui/icons";
import { PaymentBlock } from "../ui/payment-block";
import { waitText } from "../ui/problem-view";

export interface ShareActions {
  setName(name: string): void;
  commitName(): void;
  toggle(itemId: string): void;
  retry(): void;
  dismissBillUpdate(): void;
}

export function BillHeader({ bill }: { bill: BillProjection }) {
  const items = bill.items.length === 1 ? "1 item" : `${bill.items.length} items`;
  const facts = bill.date === null ? items : `${formatBillDate(bill.date)} · ${items}`;
  return (
    <section class="hero" aria-labelledby="bill-title">
      <h1 id="bill-title" class="type-title-large hero__title">
        {bill.merchant ?? "Shared bill"}
      </h1>
      <p class="type-body-medium">{facts}</p>
      <p class="type-title-small hero__label">Bill total</p>
      <HeroAmount amountMinor={bill.totalMinor} currency={bill.currency} />
    </section>
  );
}

export function StatusBanner({ share }: { share: PublicShare }) {
  if (share.status === "open") return null;
  return (
    <p class="banner type-body-large">
      <span class="banner__icon" aria-hidden="true">
        {share.status === "locking" ? <LockIcon /> : <CheckIcon />}
      </span>
      {statusSentence(share.status, share.bill)}
    </p>
  );
}

/** Why a pick was not saved, in words; [owner] is the bill's owner as a sentence names them. */
export function claimProblemText(problem: ClaimProblem, owner: string): string {
  switch (problem.kind) {
    case "name_missing":
      return "Type your name first, then tap what you had.";
    case "name_too_long":
      return `Use a name of at most ${MAX_NAME_LENGTH} characters.`;
    case "name_invalid":
      return "Your name has characters that cannot be used. Type it again.";
    case "human_check_failed":
      return "The check that you are a person could not finish. Tap your item again to retry.";
    case "turnstile":
      return "The check that you are a person did not go through. Tap your item again to retry.";
    case "full":
      return `This bill already has 30 people picking items. Ask ${owner} to add you.`;
    case "locked":
      return `Picks are closed: ${owner} is settling the split.`;
    case "rate_limited":
      return `Too many picks from this network. ${waitText(problem.retryAfterSeconds)}`;
    case "not_configured":
      return "Picking items does not work right now. You can still see the bill.";
    case "input":
      return "That pick could not be saved. Check your name, then try again.";
    case "network":
      return "Your pick was not saved: check your connection, then try again.";
    case "unexpected":
      return "Your pick was not saved. Try again in a moment.";
  }
}

interface ClaimFormProps {
  state: ShareState;
  actions: ShareActions;
  humanCheckSlot: RefObject<HTMLDivElement | null> | null;
}

export function ClaimForm({ state, actions, humanCheckSlot }: ClaimFormProps) {
  const owner = state.share === null ? UNNAMED_OWNER : ownerInSentence(state.share.bill);
  const problem = state.claimProblem;
  const nameField = useRef<HTMLInputElement>(null);
  useEffect(() => {
    // A pick without a usable name takes the person to the field to fix it.
    if (isNameProblem(problem)) nameField.current?.focus();
  }, [problem]);
  return (
    <section class="section" aria-labelledby="claim-title">
      <h2 id="claim-title" class="type-title-medium">
        Who are you?
      </h2>
      <div class="text-field">
        <label for="claim-name" class="type-body-small text-field__label">
          Your name
        </label>
        <input
          ref={nameField}
          id="claim-name"
          class="text-field__input type-body-large"
          type="text"
          value={state.name}
          maxLength={MAX_NAME_LENGTH}
          autocomplete="nickname"
          autocapitalize="words"
          spellcheck={false}
          enterKeyHint="done"
          aria-invalid={isNameProblem(problem) ? "true" : undefined}
          aria-describedby={problem === null ? "claim-help" : "claim-help claim-problem"}
          onInput={(event) => actions.setName(event.currentTarget.value)}
          onChange={() => actions.commitName()}
          onKeyDown={(event) => {
            if (event.key === "Enter") event.currentTarget.blur();
          }}
        />
      </div>
      <p id="claim-help" class="type-body-medium supporting">
        Type your name, then tap what you had. Everyone with this link sees your picks.
      </p>
      {!state.persistent && (
        <p class="notice type-body-medium">
          <NoticeIcon />
          Your picks are kept only while this page stays open. Keep it open until the bill is settled.
        </p>
      )}
      {state.removedByOwner && (
        <p class="notice type-body-medium">
          <NoticeIcon />
          {`${capitalized(owner)} took your picks off the bill. Tap what you had to pick again.`}
        </p>
      )}
      <div class="human-check" ref={humanCheckSlot} />
      <p class="type-body-medium supporting claim-progress">
        {state.checking ? "Checking that you are a person…" : state.saving ? "Saving your picks…" : ""}
      </p>
      {problem !== null && (
        <p id="claim-problem" class="error-text type-body-medium" role="alert">
          <AlertIcon />
          {claimProblemText(problem, owner)}
        </p>
      )}
    </section>
  );
}

function quantityPrefix(quantity: string | null): string {
  return quantity === null || quantity === "1" ? "" : `${quantity} × `;
}

/** How many colours people have, as in the app's palette, which tokens.css writes as `--sv-person-0` to `--sv-person-6`. */
const PERSON_COLORS = 7;

/**
 * Which of the colours of people the person named [name] has: the one the app gives the same name,
 * which picks it by the hash Java's `String.hashCode` gives the name, so that a person has one colour
 * in the app and on the page.
 */
export function personColorOf(name: string): number {
  let hash = 0;
  for (let index = 0; index < name.length; index += 1) hash = (Math.imul(31, hash) + name.charCodeAt(index)) | 0;
  return ((hash % PERSON_COLORS) + PERSON_COLORS) % PERSON_COLORS;
}

/** The chip that marks what is this browser's own: it says so in words, not by its colour alone. */
function YouChip() {
  return <span class="chip chip--you type-label-medium">You</span>;
}

interface ItemContentProps {
  item: BillItem;
  currency: Currency;
  mine: boolean;
  others: string[];
}

function ItemContent({ item, currency, mine, others }: ItemContentProps) {
  const spoken = mine ? ["you", ...others] : others;
  return (
    <>
      <span class="item__name type-body-large">
        {quantityPrefix(item.quantity)}
        {item.name}
      </span>
      <span class="item__amount type-title-medium">{formatMoney(item.amountMinor, currency)}</span>
      {spoken.length > 0 && (
        <span class="item__claimers">
          <span class="visually-hidden">{`, picked by ${listOf(spoken)}`}</span>
          <span class="chips" aria-hidden="true">
            {mine && <YouChip />}
            {others.map((name, index) => (
              <span key={index} class={`chip chip--person-${personColorOf(name)} type-label-medium`}>
                {name}
              </span>
            ))}
          </span>
        </span>
      )}
    </>
  );
}

interface ItemsSectionProps {
  share: PublicShare;
  picks: ReadonlySet<string>;
  you: string | null;
  onToggle: (itemId: string) => void;
}

export function ItemsSection({ share, picks, you, onToggle }: ItemsSectionProps) {
  const open = share.status === "open";
  const { currency } = share.bill;
  return (
    <section class="section" aria-labelledby="items-title">
      <h2 id="items-title" class="type-title-medium">
        Items
      </h2>
      {open && <p class="type-body-medium supporting">Tap what you had. Several people can share an item.</p>}
      <ul class="group">
        {share.bill.items.map((item) => {
          const mine = picks.has(item.id);
          const others = claimersOf(share, item.id)
            .filter((claim) => claim.id !== you)
            .map((claim) => claim.name);
          const content = <ItemContent item={item} currency={currency} mine={mine} others={others} />;
          return (
            <li key={item.id} class="group__item">
              {open ? (
                <button type="button" class="item item--toggle" aria-pressed={mine ? "true" : "false"} onClick={() => onToggle(item.id)}>
                  <span class="item__check" aria-hidden="true">
                    {mine && <CheckIcon />}
                  </span>
                  {content}
                </button>
              ) : (
                <div class="item">{content}</div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

export function TotalsSection({ bill }: { bill: BillProjection }) {
  return (
    <section class="section" aria-labelledby="totals-title">
      <h2 id="totals-title" class="visually-hidden">
        Charges and total
      </h2>
      <ul class="group">
        {bill.charges.map((charge) => (
          <li key={charge.id} class="group__item row">
            <span class="type-body-large">{charge.label}</span>
            <span class="type-body-large">{formatMoney(charge.amountMinor, bill.currency)}</span>
          </li>
        ))}
        <li class="group__item row row--total">
          <span class="type-title-medium">Total</span>
          <span class="type-title-medium">{formatMoney(bill.totalMinor, bill.currency)}</span>
        </li>
      </ul>
    </section>
  );
}

/**
 * What the row of [participant], who owes [amountMinor] and has no way to pay, says. When the owner
 * named who paid the whole bill, the others settle up with that person; otherwise with whoever
 * paid, as the page knows no more.
 */
export function noteWithoutPayment(participant: Participant, amountMinor: number, payer: Participant | null): string {
  if (payer !== null && participant.id === payer.id) return "Paid the bill";
  if (participant.id === OWNER_PARTICIPANT_ID) return "Shared this bill";
  if (amountMinor === 0) return "Nothing to pay";
  if (payer === null) return "No way to pay was added: settle up directly with whoever paid the bill.";
  return `No way to pay was added. ${capitalized(personInSentence(payer))} paid the bill: settle up with them directly.`;
}

interface SettledProps {
  share: PublicShare;
  /** The id of this browser's claim on the link, when it has one. */
  you: string | null;
}

/**
 * What each participant pays, and how. The amount of the participant whom the owner matched this
 * browser's claim to is marked as this person's own.
 */
export function AmountsSection({ share, you }: SettledProps) {
  const { bill, amounts } = share;
  if (amounts === null) return null;
  const payer = payerOf(share);
  const yours = matchOf(share, you);
  const rows = bill.participants.flatMap((participant) => {
    const amount = amounts[participant.id];
    return amount === undefined ? [] : [{ participant, amount }];
  });
  const payable = rows.filter((row) => row.amount.payment !== null).length;
  return (
    <section class="section" aria-labelledby="amounts-title">
      <h2 id="amounts-title" class="type-title-medium">
        What each person pays
      </h2>
      {payable > 1 && <p class="type-body-medium supporting">Tap your name to see how to pay.</p>}
      <ul class="group">
        {rows.map(({ participant, amount }) => {
          const mine = participant.id === yours?.id;
          const name = (
            <span class="person__label">
              {participantLabel(participant)}
              {mine && <YouChip />}
            </span>
          );
          const money = formatMoney(amount.amountMinor, bill.currency);
          const row = mine ? "group__item person person--you" : "group__item person";
          if (amount.payment !== null) {
            return (
              <li key={participant.id} class={row}>
                <details class="person__details" open={payable === 1}>
                  <summary class="person__row">
                    <span class="person__name type-body-large">{name}</span>
                    <span class="person__amount type-title-medium">{money}</span>
                    <span class="person__expand">
                      <ExpandIcon />
                    </span>
                  </summary>
                  <PaymentBlock payment={amount.payment} amountMinor={amount.amountMinor} currency={bill.currency} />
                </details>
              </li>
            );
          }
          const note = noteWithoutPayment(participant, amount.amountMinor, payer);
          return (
            <li key={participant.id} class={row}>
              <div class="person__row">
                <span class="person__name type-body-large">
                  {name}
                  <span class="person__note type-body-medium supporting">{note}</span>
                </span>
                <span class="person__amount type-title-medium">{money}</span>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

/**
 * Who picked items, once the split is settled, and who each of them is on the bill where the owner
 * matched their claim to a participant, or that they are not on it; shown when the owner matched
 * any. This browser's own claim is marked as such.
 */
export function ClaimersSection({ share, you }: SettledProps) {
  const rows = share.claims.map((claim) => ({ claim, participant: matchOf(share, claim.id) }));
  if (rows.every((row) => row.participant === null)) return null;
  return (
    <section class="section" aria-labelledby="claimers-title">
      <h2 id="claimers-title" class="type-title-medium">
        Who picked
      </h2>
      <ul class="group">
        {rows.map(({ claim, participant }) => (
          <li key={claim.id} class="group__item claimer">
            <span class="claimer__name type-body-large">
              {claim.name}
              {claim.id === you && <YouChip />}
            </span>
            <span class="type-body-medium supporting">
              {participant === null ? "Not on the bill" : `On the bill as ${personInSentence(participant)}`}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function LiveRegion({ announcement }: { announcement: ShareState["announcement"] }) {
  return (
    <div class="visually-hidden" aria-live="polite" aria-atomic="true">
      {announcement !== null && <span key={announcement.id}>{announcement.text}</span>}
    </div>
  );
}
