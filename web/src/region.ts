/**
 * The locale of the page's words: English, as all its text is. It names the months and gives each
 * currency its symbol, such as "₫" and "$", in every region, as the app's English texts do.
 */
export const TEXT_LOCALE = "en-US";

/**
 * Where amounts and dates are written for: a BCP 47 tag, or several, best first, as
 * `navigator.languages` gives the browser's. A page writes them as that region writes them, with
 * its marks between thousands and before decimals, the side its currency symbol goes on and the
 * order of a date, as the app does with the region of the phone.
 */
export type Region = string | readonly string[];

/** The regions the browser is set to, best first: `navigator.languages`, or `navigator.language` alone; empty when it says none. */
export function browserRegion(): readonly string[] {
  if (typeof navigator === "undefined") return [];
  if (navigator.languages !== undefined && navigator.languages.length > 0) return navigator.languages;
  return navigator.language ? [navigator.language] : [];
}

const locales = new Map<string, string>();

/**
 * The locale [region] writes amounts and dates in: the first of its tags that this browser has
 * data for, with the digits 0 to 9 also where the region has digits of its own, such as Egypt or
 * Iran. A tag that is not well formed is passed over; with none left, the page writes them as
 * [TEXT_LOCALE] does, so that no amount fails to be written.
 */
export function localeOf(region: Region): string {
  const tags = typeof region === "string" ? [region] : region;
  const key = tags.join(",");
  const known = locales.get(key);
  if (known !== undefined) return known;
  let locale = TEXT_LOCALE;
  for (const tag of tags) {
    try {
      const supported = Intl.NumberFormat.supportedLocalesOf(tag)[0];
      if (supported === undefined) continue;
      locale = new Intl.Locale(supported, { numberingSystem: "latn" }).toString();
      break;
    } catch {
      // Not a well-formed tag: the next one, if any, is tried.
    }
  }
  locales.set(key, locale);
  return locale;
}
