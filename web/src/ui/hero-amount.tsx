import type { Currency } from "../contract";
import { formatMoneyParts } from "../money";

/**
 * The step of size the amount [text] takes in the head of a page, by its length: the longer, the
 * smaller, so that it stays on one line, as the app's large amounts shrink to fit. Null for an
 * amount short enough for the largest size. The stylesheet sets the size of each step.
 */
export function amountStep(text: string): "hero__amount--long" | "hero__amount--longer" | "hero__amount--longest" | null {
  if (text.length <= 8) return null;
  if (text.length <= 10) return "hero__amount--long";
  if (text.length <= 13) return "hero__amount--longer";
  return "hero__amount--longest";
}

/**
 * The amount a page's head is about, such as a bill's total: its number large, and its currency's
 * symbol or code smaller on the side the browser's region writes it, "₫231,000" or "231.000 ₫".
 * Its text is the amount as `formatMoney` writes it, so a screen reader and a search find it whole.
 */
export function HeroAmount({ amountMinor, currency }: { amountMinor: number; currency: Currency }) {
  const { prefix, number, suffix } = formatMoneyParts(amountMinor, currency);
  const step = amountStep(prefix + number + suffix);
  return (
    <p class={step === null ? "type-display-small hero__amount" : `type-display-small hero__amount ${step}`}>
      {prefix !== "" && <span class="hero__currency hero__currency--before">{prefix}</span>}
      {number}
      {suffix !== "" && <span class="hero__currency">{suffix}</span>}
    </p>
  );
}
