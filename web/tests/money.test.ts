import { describe, expect, it, vi } from "vitest";
import { decimalOf, formatMoney, formatMoneyParts } from "../src/money";

const VND = { code: "VND", fractionDigits: 0 };
const USD = { code: "USD", fractionDigits: 2 };
const KWD = { code: "KWD", fractionDigits: 3 };
const THB = { code: "THB", fractionDigits: 2 };

/** The space that does not break, which several regions put between an amount and its symbol. */
const NBSP = "\u00a0";

describe("formatMoney in the United States", () => {
  it("writes dong without decimals", () => {
    expect(formatMoney(71500, VND, "en-US")).toBe("₫71,500");
    expect(formatMoney(0, VND, "en-US")).toBe("₫0");
  });

  it("writes dollars with two decimals", () => {
    expect(formatMoney(1234, USD, "en-US")).toBe("$12.34");
    expect(formatMoney(5, USD, "en-US")).toBe("$0.05");
    expect(formatMoney(100000, USD, "en-US")).toBe("$1,000.00");
  });

  it("writes dinars with three decimals", () => {
    expect(formatMoney(1234, KWD, "en-US")).toBe(`KWD${NBSP}1.234`);
    expect(formatMoney(1, KWD, "en-US")).toBe(`KWD${NBSP}0.001`);
  });

  it("writes a code the browser does not know after the number, as the app does", () => {
    expect(formatMoney(123456, { code: "ZZZ", fractionDigits: 2 }, "en-US")).toBe("1,234.56 ZZZ");
    expect(formatMoney(7, { code: "QQQ", fractionDigits: 0 }, "en-US")).toBe("7 QQQ");
  });

  it("writes negative charges, such as a discount", () => {
    expect(formatMoney(-1050, USD, "en-US")).toBe("-$10.50");
    expect(formatMoney(-50, USD, "en-US")).toBe("-$0.50");
  });

  it("stays exact up to the largest amount the proxy takes", () => {
    expect(formatMoney(1_000_000_000_000_000, VND, "en-US")).toBe("₫1,000,000,000,000,000");
    expect(formatMoney(999_999_999_999_999, KWD, "en-US")).toBe(`KWD${NBSP}999,999,999,999.999`);
    expect(formatMoney(999_999_999_999_999, USD, "en-US")).toBe("$9,999,999,999,999.99");
  });
});

describe("formatMoney by the browser's region, as the app writes amounts by the phone's", () => {
  it("writes the United States, Vietnam and Germany exactly so", () => {
    const amounts = (region: string) => [formatMoney(902_664, VND, region), formatMoney(123_450, USD, region), formatMoney(-1_250, USD, region)];

    expect(amounts("en-US")).toEqual(["₫902,664", "$1,234.50", "-$12.50"]);
    // Vietnam's own symbol for the dollar is "US$"; the page writes the "$" of its English.
    expect(amounts("vi-VN")).toEqual([`902.664${NBSP}₫`, `1.234,50${NBSP}$`, `-12,50${NBSP}$`]);
    expect(amounts("de-DE")).toEqual([`902.664${NBSP}₫`, `1.234,50${NBSP}$`, `-12,50${NBSP}$`]);
  });

  it("writes a bill's total of the link page as the success of the phase asks", () => {
    expect(formatMoney(231_000, VND, "vi-VN")).toBe(`231.000${NBSP}₫`);
    expect(formatMoney(231_000, VND, "en-US")).toBe("₫231,000");
  });

  it("writes France, India, Japan, Egypt and Iran with the digits 0 to 9, the English symbol and their own marks", () => {
    for (const region of ["fr-FR", "hi-IN", "ja-JP", "ar-EG", "fa-IR"]) {
      // The marks of the region with the digits 0 to 9, told by the browser rather than by the code under test.
      const marks = new Intl.NumberFormat(`${region}-u-nu-latn`).formatToParts(12345.6);
      const group = marks.find((part) => part.type === "group")?.value ?? "";
      const decimal = marks.find((part) => part.type === "decimal")?.value ?? "";
      const dollars = formatMoney(123_456_789, USD, region);
      const dong = formatMoney(902_664, VND, region);

      for (const text of [dollars, dong]) expect(text, `${region}: ${text}`).not.toMatch(/[^\P{Nd}0-9]/u);
      expect(dollars.replace(/[^0-9]/g, ""), region).toBe("123456789");
      expect(dollars, region).toContain("$");
      expect(dollars, region).not.toContain("US$");
      expect(dollars, region).toContain(`${decimal}89`);
      expect(dollars, region).toContain(group);
      expect(dong, region).toContain("₫");
    }
  });

  it("writes the digits 0 to 9 where the browser asks for other digits", () => {
    expect(formatMoney(123_450, USD, "ar-EG-u-nu-arab").replace(/[^0-9]/g, "")).toBe("123450");
  });

  it("keeps no space beside the English sign where the region spaces only its own code", () => {
    // Mexico writes "EUR 1,234.50" and Malaysia "USD 1,234.50", spaced because the code ends in a letter; the app writes the sign against the number.
    expect(formatMoney(123_450, { code: "EUR", fractionDigits: 2 }, "es-MX")).toBe("€1,234.50");
    expect(formatMoney(123_450, USD, "ms-MY")).toBe("$1,234.50");
    expect(formatMoney(123_450, { code: "CNY", fractionDigits: 2 }, "ja-JP")).toBe("CN¥1,234.50");
  });

  it("writes the currency as the region usually does in a browser that has no narrow symbols", async () => {
    // Such a browser refuses currencyDisplay "narrowSymbol"; the English sign then takes the place of the region's usual one, space and all.
    const NumberFormat = Intl.NumberFormat;
    vi.spyOn(Intl, "NumberFormat").mockImplementation(
      class extends NumberFormat {
        constructor(locales?: Intl.LocalesArgument, options?: Intl.NumberFormatOptions) {
          if (options?.currencyDisplay === "narrowSymbol") throw new RangeError("Invalid currencyDisplay: narrowSymbol");
          super(locales, options);
        }
      },
    );
    // A fresh module, whose formats were not made before the browser was changed.
    vi.resetModules();
    const older = await import("../src/money");

    expect(older.formatMoney(123_450, USD, "en-US")).toBe("$1,234.50");
    expect(older.formatMoney(23_100_000, VND, "vi-VN")).toBe(`23.100.000${NBSP}₫`);
    expect(older.formatMoney(123_450, { code: "EUR", fractionDigits: 2 }, "es-MX")).toBe(`€${NBSP}1,234.50`);
  });

  it("keeps a space that does not break between a code and a digit where the region's own symbol touched it", () => {
    // Thailand writes its own sign against the number; the page's English writes the code "THB".
    expect(formatMoney(123_450, THB, "th-TH")).toBe(`THB${NBSP}1,234.50`);
    expect(formatMoney(123_450, THB, "en-US")).toBe(`THB${NBSP}1,234.50`);
  });

  it("takes the first region of the list that the browser knows, and the page's English when it knows none or a tag is not well formed", () => {
    expect(formatMoney(902_664, VND, ["xx-XX", "vi-VN", "en-US"])).toBe(`902.664${NBSP}₫`);
    expect(formatMoney(902_664, VND, ["not a tag", "de-DE"])).toBe(`902.664${NBSP}₫`);
    expect(formatMoney(902_664, VND, ["not a tag"])).toBe("₫902,664");
    expect(formatMoney(902_664, VND, [])).toBe("₫902,664");
  });

  it("follows the languages the browser is set to unless a region is given", () => {
    vi.spyOn(navigator, "languages", "get").mockReturnValue(["de-DE", "en"]);

    expect(formatMoney(902_664, VND)).toBe(`902.664${NBSP}₫`);
  });
});

describe("formatMoneyParts", () => {
  it("puts the symbol on the side of the number the region writes it", () => {
    expect(formatMoneyParts(231_000, VND, "en-US")).toEqual({ prefix: "₫", number: "231,000", suffix: "" });
    expect(formatMoneyParts(231_000, VND, "vi-VN")).toEqual({ prefix: "", number: "231.000", suffix: `${NBSP}₫` });
    expect(formatMoneyParts(-1_250, USD, "en-US")).toEqual({ prefix: "-$", number: "12.50", suffix: "" });
    expect(formatMoneyParts(1_250, { code: "ZZZ", fractionDigits: 2 }, "de-DE")).toEqual({ prefix: "", number: "12,50", suffix: " ZZZ" });
  });

  it("gives parts that join to the text of formatMoney, with the number between them, in every region", () => {
    const amounts = [
      [VND, 902_664],
      [USD, -1_250],
      [KWD, 1_234_567],
      [THB, 42_250],
      [{ code: "ZZZ", fractionDigits: 2 }, 1_250],
    ] as const;
    for (const region of ["en-US", "vi-VN", "de-DE", "fr-FR", "hi-IN", "ja-JP", "ar-EG", "fa-IR"]) {
      for (const [currency, minor] of amounts) {
        const parts = formatMoneyParts(minor, currency, region);

        expect(parts.prefix + parts.number + parts.suffix, region).toBe(formatMoney(minor, currency, region));
        expect(parts.number, region).toMatch(/^[0-9].*[0-9]$|^[0-9]$/);
        expect(parts.prefix + parts.suffix, region).not.toMatch(/[0-9]/);
      }
    }
  });
});

describe("decimalOf", () => {
  it("splits minor units with whole numbers only", () => {
    expect(decimalOf(7150, 2)).toBe("71.50");
    expect(decimalOf(7, 3)).toBe("0.007");
    expect(decimalOf(-7, 2)).toBe("-0.07");
    expect(decimalOf(71500, 0)).toBe("71500");
  });
});
