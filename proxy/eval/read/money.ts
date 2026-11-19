/**
 * Exact arithmetic on amounts, as the app does it with `BigDecimal`: an amount in the answer is a
 * decimal in the currency's main unit (12.5), the app works in whole minor units (1250 cents), and
 * every rounding is half up, away from zero. A JSON number reaches the app as the proxy writes it,
 * which is its shortest decimal form, so that form is what is rounded. Node runs this file as it
 * is, its types stripped.
 */

/** [value] as a whole number of units of 10^-scale: 12.5 is 125 units at scale 1. */
function exact(value: number): { units: bigint; scale: number } {
  const text = String(value);
  if (!/^-?\d+(\.\d+)?$/.test(text)) throw new Error(`${text} is not a plain decimal`);
  const [whole, fraction = ""] = text.replace("-", "").split(".");
  const units = BigInt(whole + fraction);
  return { units: value < 0 ? -units : units, scale: fraction.length };
}

/** [units] at [scale] rounded to a whole number, half away from zero. */
function halfUp(units: bigint, scale: number): number {
  const divisor = 10n ** BigInt(scale);
  const magnitude = units < 0n ? -units : units;
  const rounded = magnitude / divisor + ((magnitude % divisor) * 2n >= divisor ? 1n : 0n);
  return Number(units < 0n ? -rounded : rounded);
}

/**
 * [amount], in the main unit, as whole minor units of a currency with [digits] decimals, as the
 * app's `BillCurrency.toMinor` makes it. A number too small for a plain decimal is no amount on a
 * bill, and counts as 0.
 */
export function minorOf(amount: number, digits: number): number {
  if (/e/i.test(String(amount))) return Math.abs(amount) < 1 ? 0 : Math.round(amount * 10 ** digits);
  const { units, scale } = exact(amount);
  return scale <= digits ? Number(units * 10n ** BigInt(digits - scale)) : halfUp(units, scale - digits);
}

/** [minor] units of a currency with [digits] decimals, in its main unit: 1250 cents are 12.5. */
export function majorOf(minor: number, digits: number): number {
  const { units, scale } = exact(minor);
  const total = scale + digits;
  const negative = units < 0n;
  const text = (negative ? -units : units).toString().padStart(total + 1, "0");
  const point = text.length - total;
  return Number(`${negative ? "-" : ""}${text.slice(0, point)}${total > 0 ? `.${text.slice(point)}` : ""}`);
}

/** [value] times 10^[places], exactly: a unit price of 5.299 dollars is 529.9 cents. */
export function scaled(value: number, places: number): number {
  const { units, scale } = exact(value);
  return places >= scale ? Number(units * 10n ** BigInt(places - scale)) : majorOf(Number(units), scale - places);
}

/** [quantity] times a price of [unit] minor units, rounded half up to whole minor units, as a till prints a line. */
export function times(quantity: number, unit: number): number {
  const one = exact(quantity);
  const other = exact(unit);
  return halfUp(one.units * other.units, one.scale + other.scale);
}

/** [rate] percent of [base] minor units, rounded half up to whole minor units. */
export function percentOf(base: number, rate: number): number {
  const { units, scale } = exact(rate);
  return halfUp(BigInt(base) * units, scale + 2);
}

/** How many decimals [amount] is written with, as the app counts them after dropping trailing zeros; 0 for a whole number. */
export function decimalsOf(amount: number): number {
  const text = String(amount);
  const exponent = /e([+-]\d+)$/i.exec(text);
  const [mantissa] = text.split(/e/i);
  const fraction = mantissa.split(".")[1] ?? "";
  return Math.max(fraction.length - (exponent === null ? 0 : Number(exponent[1])), 0);
}
