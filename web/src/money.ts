import type { Currency } from "./contract";

const formats = new Map<string, Intl.NumberFormat>();

let knownCurrencies: ReadonlySet<string> | null | undefined;

/** The currency codes this browser has data for, or null when it cannot say. */
function supportedCurrencies(): ReadonlySet<string> | null {
  if (knownCurrencies === undefined) {
    try {
      knownCurrencies = new Set(Intl.supportedValuesOf("currency"));
    } catch {
      knownCurrencies = null;
    }
  }
  return knownCurrencies;
}

function numberFormat(code: string | null, fractionDigits: number): Intl.NumberFormat | null {
  const id = `${code ?? ""}:${fractionDigits}`;
  const cached = formats.get(id);
  if (cached) return cached;
  try {
    const format = new Intl.NumberFormat("en", {
      ...(code === null ? {} : { style: "currency", currency: code }),
      minimumFractionDigits: fractionDigits,
      maximumFractionDigits: fractionDigits,
    });
    formats.set(id, format);
    return format;
  } catch {
    return null;
  }
}

/**
 * [amountMinor] as an exact decimal, worked out with whole numbers only: 7150 with 2 digits is
 * "71.50". Intl formats a numeric string exactly where it can, and as the nearest number
 * elsewhere, which is exact for every amount up to 10^15 minor units.
 */
export function decimalOf(amountMinor: number, fractionDigits: number): `${number}` {
  const negative = amountMinor < 0;
  const magnitude = Math.abs(amountMinor);
  const scale = 10 ** fractionDigits;
  const fraction = magnitude % scale;
  const whole = (magnitude - fraction) / scale;
  const digits = fractionDigits === 0 ? `${whole}` : `${whole}.${String(fraction).padStart(fractionDigits, "0")}`;
  // The text is built from two whole numbers and a dot, so it always reads as a number.
  return (negative ? `-${digits}` : digits) as `${number}`;
}

/**
 * An amount of [currency] in minor units, as English text with the currency's own digits:
 * "₫71,500", "$12.34", "KWD 1.234". A code the browser does not know is written as
 * "<code> <number>".
 */
export function formatMoney(amountMinor: number, currency: Currency): string {
  const { code, fractionDigits } = currency;
  const decimal = decimalOf(amountMinor, fractionDigits);
  const known = supportedCurrencies();
  const currencyFormat = known === null || known.has(code) ? numberFormat(code, fractionDigits) : null;
  if (currencyFormat) return currencyFormat.format(decimal);
  const plain = numberFormat(null, fractionDigits);
  return `${code} ${plain ? plain.format(decimal) : decimal}`;
}
