import type { Currency } from "./contract";
import { type Region, TEXT_LOCALE, browserRegion, localeOf } from "./region";

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

/** A new format, or null when the browser refuses [options]. */
function formatOrNull(locale: string, options: Intl.NumberFormatOptions): Intl.NumberFormat | null {
  try {
    return new Intl.NumberFormat(locale, options);
  } catch {
    return null;
  }
}

/**
 * A format of [locale] for amounts of [code] with [fractionDigits] decimals, or for plain numbers
 * without [code]; null when the browser has none. With [narrow], the currency is shown by its
 * narrow symbol, such as "€" where Mexico writes "EUR", where the browser can.
 */
function numberFormat(locale: string, code: string | null, fractionDigits: number, narrow = false): Intl.NumberFormat | null {
  const id = `${locale}|${code ?? ""}|${fractionDigits}|${narrow}`;
  const cached = formats.get(id);
  if (cached) return cached;
  const options: Intl.NumberFormatOptions = {
    ...(code === null ? {} : { style: "currency", currency: code }),
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  };
  // A browser that does not know the narrow symbol refuses the option; it writes the currency its usual way.
  const format = (narrow && code !== null ? formatOrNull(locale, { ...options, currencyDisplay: "narrowSymbol" }) : null) ?? formatOrNull(locale, options);
  if (format !== null) formats.set(id, format);
  return format;
}

/** The symbol the page's English writes [code] with, such as "₫" for VND, "$" for USD and "THB" for THB. */
function englishSymbol(code: string): string {
  return numberFormat(TEXT_LOCALE, code, 0)?.formatToParts(0).find((part) => part.type === "currency")?.value ?? code;
}

/** Whether [part] holds digits of the number. */
function isDigits(part: Intl.NumberFormatPart | undefined): boolean {
  return part?.type === "integer" || part?.type === "fraction";
}

/**
 * The amount [parts] write, with [symbol] where the region puts its own currency symbol. A symbol
 * that ends or begins with a letter, such as "THB", keeps a space that does not break from a digit
 * it would touch, as ICU spaces a code on a phone: "THB 1,234.50".
 */
function withSymbol(parts: readonly Intl.NumberFormatPart[], symbol: string): string {
  return parts
    .map((part, index) => {
      if (part.type !== "currency") return part.value;
      const before = isDigits(parts[index - 1]) && /^\p{L}/u.test(symbol) ? "\u00a0" : "";
      const after = isDigits(parts[index + 1]) && /\p{L}$/u.test(symbol) ? "\u00a0" : "";
      return `${before}${symbol}${after}`;
    })
    .join("");
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
 * An amount of [currency] in minor units, written for [region], the browser's unless another is
 * given, as the app writes it for the phone's region: "₫71,500" in the United States, "71.500 ₫"
 * in Vietnam and Germany. The region decides the marks between thousands and before decimals, the
 * side of the number the symbol goes on and the space beside it. The digits are 0 to 9, the number
 * of decimals is the currency's, and the symbol is the one of the page's English everywhere, such
 * as "$" where Vietnam writes "US$". A code the browser does not know follows the number after a
 * space: "1,234.56 ZZZ".
 */
export function formatMoney(amountMinor: number, currency: Currency, region: Region = browserRegion()): string {
  const { code, fractionDigits } = currency;
  const locale = localeOf(region);
  const decimal = decimalOf(amountMinor, fractionDigits);
  const known = supportedCurrencies();
  // The region's parts with its narrow symbol, so that no space is kept that the region puts only
  // beside a code of letters, such as Mexico's "EUR 1,234.50" for the "€1,234.50" the app writes.
  const currencyFormat = known === null || known.has(code) ? numberFormat(locale, code, fractionDigits, true) : null;
  if (currencyFormat) return withSymbol(currencyFormat.formatToParts(decimal), englishSymbol(code));
  const plain = numberFormat(locale, null, fractionDigits);
  return `${plain ? plain.format(decimal) : decimal} ${code}`;
}

/**
 * The text of [formatMoney] in three parts, for an amount shown large with its symbol smaller
 * beside it: the [AmountParts.number] with its marks, and what comes before and after it, either of
 * which holds the symbol or code on its side of the number in the region. Joined, they are the
 * text of [formatMoney].
 */
export interface AmountParts {
  prefix: string;
  number: string;
  suffix: string;
}

/** [formatMoney]'s amount as [AmountParts]: "₫" and "231,000" in the United States, "231.000" and " ₫" in Vietnam. */
export function formatMoneyParts(amountMinor: number, currency: Currency, region: Region = browserRegion()): AmountParts {
  const text = formatMoney(amountMinor, currency, region);
  // The digits are 0 to 9, and a currency's symbol or code never holds one.
  const first = text.search(/[0-9]/);
  const last = text.search(/[0-9](?=[^0-9]*$)/);
  if (first < 0) return { prefix: "", number: text, suffix: "" };
  return { prefix: text.slice(0, first), number: text.slice(first, last + 1), suffix: text.slice(last + 1) };
}
