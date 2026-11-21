import { type Region, browserRegion, localeOf } from "./region";

const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

/** The English names of the months as a date writes them short, as the app's texts have them. */
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"] as const;

/**
 * The order in which a region writes the day, the month and the year of a date with the name of
 * its month: the day first in Vietnam and Germany, the month first in the United States, the year
 * first in Japan. The page writes its dates in English words, so the order is all it takes from
 * the region, as the app does.
 */
export type DateOrder = "day-first" | "month-first" | "year-first";

const orders = new Map<string, DateOrder>();

/**
 * The order of a date in [region], the browser's unless another is given, read from the way it
 * writes a date of the Gregorian calendar with the name of its month, as the app reads it from
 * the region's medium pattern. A browser that cannot say gives the order of the United States.
 */
export function dateOrderOf(region: Region = browserRegion()): DateOrder {
  const locale = localeOf(region);
  const known = orders.get(locale);
  if (known !== undefined) return known;
  let order: DateOrder = "month-first";
  try {
    const parts = new Intl.DateTimeFormat(locale, { dateStyle: "medium", calendar: "gregory", timeZone: "UTC" }).formatToParts(Date.UTC(2026, 10, 13));
    const first = parts.find((part) => part.type === "day" || part.type === "month" || part.type === "year")?.type;
    order = first === "year" ? "year-first" : first === "month" ? "month-first" : "day-first";
  } catch {
    // The order of the page's English stands.
  }
  orders.set(locale, order);
  return order;
}

/**
 * A bill's day, `YYYY-MM-DD`, with its year, in the order of [region], the browser's unless
 * another is given, and with the English name of its month: "14 Nov 2026" where the day comes
 * first, "Nov 14, 2026" where the month does, "2026 Nov 14" where the year does, as the app writes
 * a date with its year. Text that is not a real day is shown as it is.
 */
export function formatBillDate(date: string, region: Region = browserRegion()): string {
  const match = DAY.exec(date);
  if (match === null) return date;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const back = new Date(Date.UTC(year, month - 1, day));
  if (back.getUTCFullYear() !== year || back.getUTCMonth() !== month - 1 || back.getUTCDate() !== day) return date;
  const name = MONTHS[month - 1];
  const yearText = match[1];
  switch (dateOrderOf(region)) {
    case "day-first":
      return `${day} ${name} ${yearText}`;
    case "month-first":
      return `${name} ${day}, ${yearText}`;
    case "year-first":
      return `${yearText} ${name} ${day}`;
  }
}
