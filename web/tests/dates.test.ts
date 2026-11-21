import { describe, expect, it, vi } from "vitest";
import { dateOrderOf, formatBillDate } from "../src/dates";

describe("dateOrderOf", () => {
  it("gives each region the order the app gives it, read from a date with the name of its month", () => {
    const regions = ["en-US", "vi-VN", "de-DE", "fr-FR", "hi-IN", "ar-EG", "ja-JP", "fa-IR", "en-CA", "sv-SE"];

    expect(Object.fromEntries(regions.map((region) => [region, dateOrderOf(region)]))).toEqual({
      "en-US": "month-first",
      "vi-VN": "day-first",
      "de-DE": "day-first",
      "fr-FR": "day-first",
      "hi-IN": "day-first",
      "ar-EG": "day-first",
      "ja-JP": "year-first",
      "fa-IR": "day-first",
      // Both write a date of digits alone with the year first, but not one with a month name.
      "en-CA": "month-first",
      "sv-SE": "day-first",
    });
  });
});

describe("formatBillDate", () => {
  it("writes the United States, Vietnam and Germany exactly so, with the English names of the months", () => {
    expect(formatBillDate("2026-11-14", "en-US")).toBe("Nov 14, 2026");
    expect(formatBillDate("2026-11-14", "vi-VN")).toBe("14 Nov 2026");
    expect(formatBillDate("2026-11-14", "de-DE")).toBe("14 Nov 2026");
  });

  it("writes a date in each of the three orders, with the digits 0 to 9", () => {
    expect(formatBillDate("2027-01-02", "ja-JP")).toBe("2027 Jan 2");
    expect(formatBillDate("2027-09-30", "ar-EG")).toBe("30 Sep 2027");
    expect(formatBillDate("2027-09-30", "fa-IR")).toBe("30 Sep 2027");
    expect(formatBillDate("2027-05-09", "en-CA")).toBe("May 9, 2027");
  });

  it("names every month", () => {
    const months = Array.from({ length: 12 }, (_, index) => formatBillDate(`2026-${String(index + 1).padStart(2, "0")}-01`, "en-US").split(" ")[0]);

    expect(months).toEqual(["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]);
  });

  it("shows text that is not a real day as it is", () => {
    for (const text of ["2026-02-30", "2026-13-01", "14/11/2026", "", "Nov 14"]) expect(formatBillDate(text, "vi-VN")).toBe(text);
  });

  it("follows the languages the browser is set to unless a region is given", () => {
    vi.spyOn(navigator, "languages", "get").mockReturnValue(["vi-VN", "en"]);

    expect(formatBillDate("2026-11-14")).toBe("14 Nov 2026");
  });
});
