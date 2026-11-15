import { describe, expect, it } from "vitest";
import { decimalOf, formatMoney } from "../src/money";

const VND = { code: "VND", fractionDigits: 0 };
const USD = { code: "USD", fractionDigits: 2 };
const KWD = { code: "KWD", fractionDigits: 3 };

describe("formatMoney", () => {
  it("writes dong without decimals", () => {
    expect(formatMoney(71500, VND)).toBe("₫71,500");
    expect(formatMoney(0, VND)).toBe("₫0");
  });

  it("writes dollars with two decimals", () => {
    expect(formatMoney(1234, USD)).toBe("$12.34");
    expect(formatMoney(5, USD)).toBe("$0.05");
    expect(formatMoney(100000, USD)).toBe("$1,000.00");
  });

  it("writes dinars with three decimals", () => {
    expect(formatMoney(1234, KWD)).toBe("KWD 1.234");
    expect(formatMoney(1, KWD)).toBe("KWD 0.001");
  });

  it("writes a code the browser does not know before the number", () => {
    expect(formatMoney(123456, { code: "ZZZ", fractionDigits: 2 })).toBe("ZZZ 1,234.56");
    expect(formatMoney(7, { code: "QQQ", fractionDigits: 0 })).toBe("QQQ 7");
  });

  it("writes negative charges, such as a discount", () => {
    expect(formatMoney(-1050, USD)).toBe("-$10.50");
    expect(formatMoney(-50, USD)).toBe("-$0.50");
  });

  it("stays exact up to the largest amount the proxy takes", () => {
    expect(formatMoney(1_000_000_000_000_000, VND)).toBe("₫1,000,000,000,000,000");
    expect(formatMoney(999_999_999_999_999, KWD)).toBe("KWD 999,999,999,999.999");
    expect(formatMoney(999_999_999_999_999, USD)).toBe("$9,999,999,999,999.99");
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
