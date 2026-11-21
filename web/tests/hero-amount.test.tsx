import { describe, expect, it, vi } from "vitest";
import { HeroAmount, amountStep } from "../src/ui/hero-amount";
import { mount } from "./render";

const VND = { code: "VND", fractionDigits: 0 };

describe("the amount in a page's head", () => {
  it("sets the symbol apart before the number where the region writes it first", () => {
    vi.spyOn(navigator, "languages", "get").mockReturnValue(["en-US"]);
    const amount = mount(<HeroAmount amountMinor={231_000} currency={VND} />).querySelector(".hero__amount");

    expect(amount?.textContent).toBe("₫231,000");
    expect(Array.from(amount?.querySelectorAll(".hero__currency") ?? [], (part) => [part.className, part.textContent])).toEqual([
      ["hero__currency hero__currency--before", "₫"],
    ]);
  });

  it("sets the symbol apart after the number where the region writes it last", () => {
    vi.spyOn(navigator, "languages", "get").mockReturnValue(["vi-VN"]);
    const amount = mount(<HeroAmount amountMinor={231_000} currency={VND} />).querySelector(".hero__amount");

    expect(amount?.textContent).toBe("231.000 ₫");
    expect(Array.from(amount?.querySelectorAll(".hero__currency") ?? [], (part) => [part.className, part.textContent])).toEqual([
      ["hero__currency", " ₫"],
    ]);
  });

  it("takes a smaller step the longer the amount, so that it stays on one line", () => {
    expect(amountStep("₫231,000")).toBeNull();
    expect(amountStep("$1,234.56")).toBe("hero__amount--long");
    expect(amountStep("₫2,350,000")).toBe("hero__amount--long");
    expect(amountStep("₫150,000,000")).toBe("hero__amount--longer");
    expect(amountStep("$1,234,567.89")).toBe("hero__amount--longer");
    expect(amountStep("₫1,500,000,000")).toBe("hero__amount--longest");

    vi.spyOn(navigator, "languages", "get").mockReturnValue(["en-US"]);
    const long = mount(<HeroAmount amountMinor={150_000_000} currency={VND} />).querySelector(".hero__amount");
    expect(long?.className).toBe("type-display-small hero__amount hero__amount--longer");
  });
});
