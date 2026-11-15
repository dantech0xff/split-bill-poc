import { afterEach, describe, expect, it, vi } from "vitest";
import { act } from "preact/test-utils";
import { PaymentBlock } from "../src/ui/payment-block";
import { mount, stubCanvas, textOf } from "./render";

const VND = { code: "VND", fractionDigits: 0 };

afterEach(() => {
  vi.restoreAllMocks();
});

function click(element: Element | null): Promise<void> {
  return act(async () => {
    (element as HTMLElement | null)?.click();
  });
}

describe("PaymentBlock", () => {
  it("copies the details to the clipboard", async () => {
    const writeText = vi.fn(async () => undefined);
    vi.spyOn(navigator, "clipboard", "get").mockReturnValue({ writeText } as unknown as Clipboard);
    const page = mount(<PaymentBlock payment={{ link: null, qrPayload: null, details: "Bank: ACB\nAccount: 257678859", amountFilledIn: false }} amountMinor={67500} currency={VND} />);
    await click(page.querySelector(".details-card button"));
    expect(writeText).toHaveBeenCalledWith("Bank: ACB\nAccount: 257678859");
    await vi.waitFor(() => expect(textOf(page.querySelector('[role="status"]'))).toBe("Copied."));
  });

  it("selects the details when the clipboard refuses", async () => {
    vi.spyOn(navigator, "clipboard", "get").mockReturnValue({
      writeText: async () => {
        throw new DOMException("Denied", "NotAllowedError");
      },
    } as unknown as Clipboard);
    const page = mount(<PaymentBlock payment={{ link: null, qrPayload: null, details: "Account: 257678859", amountFilledIn: false }} amountMinor={67500} currency={VND} />);
    await click(page.querySelector(".details-card button"));
    await vi.waitFor(() =>
      expect(textOf(page.querySelector('[role="status"]'))).toBe("The details are selected: copy them from your browser's menu."),
    );
    expect(window.getSelection()?.toString()).toBe("Account: 257678859");
  });

  it("draws the QR code on its canvas, one pixel per module with a quiet zone", () => {
    const putImageData = stubCanvas();
    const page = mount(<PaymentBlock payment={{ link: null, qrPayload: "upi://pay?pa=lan@okbank&am=1500.00", details: "UPI", amountFilledIn: true }} amountMinor={150000} currency={VND} />);
    const canvas = page.querySelector<HTMLCanvasElement>("canvas");
    expect(putImageData).toHaveBeenCalledOnce();
    expect(canvas?.width).toBe(canvas?.height);
    expect((canvas?.width ?? 0) - 8).toBeGreaterThanOrEqual(21);
  });

  it("says so when the QR code cannot be drawn, and still shows the details", () => {
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);
    const page = mount(<PaymentBlock payment={{ link: null, qrPayload: "upi://pay?pa=lan@okbank", details: "UPI: lan@okbank", amountFilledIn: true }} amountMinor={150000} currency={VND} />);
    expect(page.querySelector("canvas")).toBeNull();
    expect(textOf(page)).toContain("The QR code could not be drawn here.");
    expect(textOf(page)).toContain("UPI: lan@okbank");
  });
});
