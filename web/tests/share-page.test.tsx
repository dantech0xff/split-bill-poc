import { type Mock, beforeEach, describe, expect, it, vi } from "vitest";
import { parseOpenAnswer } from "../src/contract";
import { ShareView } from "../src/pages/share-page";
import { type ShareActions, personColorOf } from "../src/pages/share-sections";
import type { ShareState } from "../src/share-controller";
import { ReportAbuse } from "../src/ui/payment-block";
import shareLocked from "./fixtures/share-locked.json";
import shareLocking from "./fixtures/share-locking.json";
import shareOpen from "./fixtures/share-open.json";
import { all, mount, stubCanvas, textOf } from "./render";

function stateOf(answer: unknown, patch: Partial<ShareState> = {}): ShareState {
  const parsed = parseOpenAnswer(answer);
  if (parsed === null) throw new Error("The fixture does not parse");
  return {
    phase: "ready",
    problem: null,
    share: parsed.share,
    you: parsed.you,
    name: "",
    picks: null,
    saving: false,
    checking: false,
    claimProblem: null,
    persistent: true,
    announcement: null,
    billUpdated: false,
    removedByOwner: false,
    ...patch,
  };
}

function actions(): { [K in keyof ShareActions]: Mock<ShareActions[K]> } {
  return {
    setName: vi.fn<ShareActions["setName"]>(),
    commitName: vi.fn<ShareActions["commitName"]>(),
    toggle: vi.fn<ShareActions["toggle"]>(),
    retry: vi.fn<ShareActions["retry"]>(),
    dismissBillUpdate: vi.fn<ShareActions["dismissBillUpdate"]>(),
  };
}

beforeEach(() => {
  stubCanvas();
});

function loadingState(patch: Partial<ShareState> = {}): ShareState {
  return { ...stateOf(shareOpen), share: null, phase: "loading", ...patch };
}

describe("the bill link's page", () => {
  it("shows the bill while picks are open, with who picked what", () => {
    const page = mount(<ShareView state={stateOf(shareOpen)} actions={actions()} />);
    expect(textOf(page.querySelector("h1"))).toBe("Pho 24");
    expect(textOf(page.querySelector(".hero"))).toContain("Nov 14, 2026 · 3 items");
    // The symbol is a piece of its own, set smaller beside the number; the amount's text is whole.
    expect(page.querySelector(".hero__amount")?.textContent).toBe("₫231,000");
    const items = all(page, "button[aria-pressed]");
    expect(items.map((item) => item.getAttribute("aria-pressed"))).toEqual(["false", "false", "false"]);
    expect(textOf(items[0] ?? null)).toBe("2 × Pho bo ₫130,000 , picked by Minh and An Minh An");
    expect(textOf(items[1] ?? null)).toContain("Spring rolls ₫45,000");
    expect(textOf(items[2] ?? null)).toBe("Iced coffee ₫35,000");
    expect(textOf(page.querySelector('[aria-labelledby="totals-title"]'))).toBe("Charges and total Service charge ₫21,000 Total ₫231,000");
    const input = page.querySelector<HTMLInputElement>("input#claim-name");
    expect(textOf(page.querySelector('label[for="claim-name"]'))).toBe("Your name");
    expect(input?.maxLength).toBe(40);
  });

  it("writes the total and the date as a browser set to Vietnam writes them", () => {
    vi.spyOn(navigator, "languages", "get").mockReturnValue(["vi-VN"]);
    const page = mount(<ShareView state={stateOf(shareOpen)} actions={actions()} />);

    expect(page.querySelector(".hero__amount")?.textContent).toBe("231.000\u00a0₫");
    expect(textOf(page.querySelector(".hero"))).toContain("14 Nov 2026 · 3 items");
    expect(textOf(page.querySelector('[aria-labelledby="totals-title"]'))).toBe("Charges and total Service charge 21.000 ₫ Total 231.000 ₫");
  });

  it("passes taps and typing on to the page's controller", () => {
    const on = actions();
    const page = mount(<ShareView state={stateOf(shareOpen)} actions={on} />);
    (all(page, "button[aria-pressed]")[1] as HTMLButtonElement).click();
    expect(on.toggle).toHaveBeenCalledWith("i2");
    const input = page.querySelector<HTMLInputElement>("#claim-name");
    if (input === null) throw new Error("No name field");
    input.value = "Bao";
    input.dispatchEvent(new Event("input", { bubbles: true }));
    expect(on.setName).toHaveBeenCalledWith("Bao");
    input.dispatchEvent(new Event("change", { bubbles: true }));
    expect(on.commitName).toHaveBeenCalled();
  });

  it("marks this browser's picks as pressed, including picks on their way", () => {
    const mine = mount(<ShareView state={stateOf(shareOpen, { you: "c-minh", name: "Minh" })} actions={actions()} />);
    const pressed = all(mine, "button[aria-pressed]").map((item) => item.getAttribute("aria-pressed"));
    expect(pressed).toEqual(["true", "false", "false"]);
    expect(textOf(all(mine, "button[aria-pressed]")[0] ?? null)).toBe("2 × Pho bo ₫130,000 , picked by you and An You An");

    const pending = mount(<ShareView state={stateOf(shareOpen, { you: "c-minh", picks: ["i3"] })} actions={actions()} />);
    expect(all(pending, "button[aria-pressed]").map((item) => item.getAttribute("aria-pressed"))).toEqual(["false", "false", "true"]);
  });

  it("says when picks last only while the page stays open", () => {
    const page = mount(<ShareView state={stateOf(shareOpen, { persistent: false })} actions={actions()} />);
    expect(textOf(page)).toContain("Your picks are kept only while this page stays open.");
  });

  it("says why a pick was not saved", () => {
    const page = mount(<ShareView state={stateOf(shareOpen, { claimProblem: { kind: "full" } })} actions={actions()} />);
    expect(textOf(page.querySelector('[role="alert"]'))).toBe("This bill already has 30 people picking items. Ask Lan to add you.");
    const limited = mount(<ShareView state={stateOf(shareOpen, { claimProblem: { kind: "rate_limited", retryAfterSeconds: 30 } })} actions={actions()} />);
    expect(textOf(limited.querySelector('[role="alert"]'))).toBe("Too many picks from this network. Try again in 30 seconds.");
  });

  it("takes the person to the name field when a pick needs a name", () => {
    const page = mount(<ShareView state={stateOf(shareOpen, { claimProblem: { kind: "name_missing" } })} actions={actions()} />);
    expect(document.activeElement).toBe(page.querySelector("#claim-name"));
    expect(page.querySelector("#claim-name")?.getAttribute("aria-invalid")).toBe("true");
    expect(textOf(page.querySelector('[role="alert"]'))).toBe("Type your name first, then tap what you had.");
  });

  it("freezes the picks while the payer settles the split", () => {
    const page = mount(<ShareView state={stateOf(shareLocking)} actions={actions()} />);
    expect(textOf(page.querySelector(".banner"))).toBe("Picks are closed: Lan is settling the split.");
    expect(all(page, "button[aria-pressed]")).toHaveLength(0);
    expect(page.querySelector("input")).toBeNull();
    expect(textOf(all(page, ".item")[0] ?? null)).toContain("picked by you and An");
  });

  it("lists what each person pays and how, once the split is settled", () => {
    const page = mount(<ShareView state={stateOf(shareLocked)} actions={actions()} />);
    expect(textOf(page.querySelector(".banner"))).toBe("The split is settled. Each person's amount is below.");
    const people = all(page, ".person");
    expect(people.map((person) => textOf(person.querySelector(".person__row")))).toEqual([
      "The bill's owner Shared this bill ₫77,000",
      "Minh ₫86,500",
      "An ₫67,500",
    ]);
    const pay = page.querySelector<HTMLAnchorElement>('a[href="https://paypal.me/lan/86500VND"]');
    expect(textOf(pay)).toBe("Pay on paypal.me");
    expect(pay?.target).toBe("_blank");
    expect(pay?.rel).toBe("noopener noreferrer");
    expect(textOf(people[1] ?? null)).toContain("The amount, ₫86,500, is filled in for you.");
    const qr = people[2]?.querySelector('canvas[role="img"]');
    expect(qr?.getAttribute("aria-label")).toBe("QR code to pay ₫67,500");
    expect(textOf(people[2] ?? null)).toMatch(/Account: 0{9}(?!0)/);
    expect(textOf(people[2] ?? null)).toContain("Type in the amount yourself: ₫67,500.");
    expect(all(page, "details").map((details) => (details as HTMLDetailsElement).open)).toEqual([false, false]);
  });

  it("says beside each way to pay that the person who sent the link wrote it and Split Vibe does not check it, with Report abuse when the page has an address", () => {
    const notice = "The person who sent this link wrote these payment details. Split Vibe does not check them.";
    const without = mount(<ShareView state={stateOf(shareLocked)} actions={actions()} />);
    expect(all(without, ".payment").map((block) => textOf(block.firstElementChild))).toEqual([notice, notice]);

    const report = "mailto:abuse@example.com?subject=Report%20abuse%3A%20Split%20Vibe%20bill%20link";
    const page = mount(
      <ReportAbuse.Provider value={report}>
        <ShareView state={stateOf(shareLocked)} actions={actions()} />
      </ReportAbuse.Provider>,
    );
    const blocks = all(page, ".payment");
    expect(blocks).toHaveLength(2);
    for (const block of blocks) {
      expect(textOf(block.firstElementChild)).toBe(`${notice} Report abuse`);
      expect(block.firstElementChild?.querySelector("a")?.getAttribute("href")).toBe(report);
    }
  });

  it("opens the only way to pay at once, and says to settle up with whoever paid when nobody was named", () => {
    const answer = structuredClone(shareLocked);
    answer.share.amounts.p2.payment = null as never;
    const page = mount(<ShareView state={stateOf(answer)} actions={actions()} />);
    expect(all(page, "details").map((details) => (details as HTMLDetailsElement).open)).toEqual([true]);
    expect(textOf(all(page, ".person")[2] ?? null)).toBe("An No way to pay was added: settle up directly with whoever paid the bill. ₫67,500");
    expect(textOf(page)).not.toContain("paid the bill:");
  });

  it("says who paid the bill, and to settle up with them, where an amount has no way to pay", () => {
    const answer = structuredClone(shareLocked) as { share: Record<string, unknown> & { amounts: Record<string, { payment: unknown }> } };
    answer.share.paidBy = "p1";
    for (const id of ["p1", "p2"]) answer.share.amounts[id]!.payment = null;
    const page = mount(<ShareView state={stateOf(answer)} actions={actions()} />);
    expect(all(page, ".person").map((person) => textOf(person))).toEqual([
      "The bill's owner Shared this bill ₫77,000",
      "Minh Paid the bill ₫86,500",
      "An No way to pay was added. Minh paid the bill: settle up with them directly. ₫67,500",
    ]);
  });

  it("calls an owner who paid and left their name empty the person who made this link", () => {
    const answer = structuredClone(shareLocked) as { share: Record<string, unknown> & { amounts: Record<string, { payment: unknown }> } };
    answer.share.paidBy = "me";
    answer.share.amounts.p2!.payment = null;
    const page = mount(<ShareView state={stateOf(answer)} actions={actions()} />);
    const people = all(page, ".person");
    expect(textOf(people[0]?.querySelector(".person__row") ?? null)).toBe("The bill's owner Paid the bill ₫77,000");
    expect(textOf(people[2] ?? null)).toBe("An No way to pay was added. The person who made this link paid the bill: settle up with them directly. ₫67,500");
  });

  it("says who each claim is on the bill once the split is settled, and marks the amount of this browser's own", () => {
    const page = mount(<ShareView state={stateOf(shareLocked, { you: "c-minh" })} actions={actions()} />);
    expect(textOf(page.querySelector("#claimers-title"))).toBe("Who picked");
    expect(all(page, ".claimer").map((claimer) => textOf(claimer))).toEqual(["Minh You On the bill as Minh", "An On the bill as An"]);
    expect(all(page, ".person").map((person) => textOf(person.querySelector(".person__row")))).toEqual([
      "The bill's owner Shared this bill ₫77,000",
      "Minh You ₫86,500",
      "An ₫67,500",
    ]);
    const marked = all(page, ".person--you");
    expect(marked).toHaveLength(1);
    // Words say whose amount it is, not a colour alone.
    expect(textOf(marked[0]?.querySelector(".person__name .chip--you") ?? null)).toBe("You");
    expect(marked[0]?.querySelector("summary")?.textContent).toContain("Minh");
  });

  it("marks no amount when this browser's claim was matched to nobody, and still marks its claim", () => {
    const answer = structuredClone(shareLocked) as { share: Record<string, unknown> };
    answer.share.matches = { "c-minh": "p1" };
    const page = mount(<ShareView state={stateOf(answer, { you: "c-an" })} actions={actions()} />);
    expect(all(page, ".claimer").map((claimer) => textOf(claimer))).toEqual(["Minh On the bill as Minh", "An You Not on the bill"]);
    expect(all(page, ".person--you")).toHaveLength(0);
    expect(all(page, ".person .chip--you")).toHaveLength(0);
  });

  it("lists who picked only once the owner matched a claim to someone on the bill", () => {
    for (const matches of [null, { "c-minh": "p9" }]) {
      const answer = structuredClone(shareLocked) as { share: Record<string, unknown> };
      answer.share.matches = matches;
      const page = mount(<ShareView state={stateOf(answer, { you: "c-minh" })} actions={actions()} />);
      expect(page.querySelector("#claimers-title"), JSON.stringify(matches)).toBeNull();
      expect(all(page, ".claimer")).toHaveLength(0);
      expect(all(page, ".person--you")).toHaveLength(0);
    }
    const locking = mount(<ShareView state={stateOf(shareLocking)} actions={actions()} />);
    expect(locking.querySelector("#claimers-title")).toBeNull();
  });

  it("says a claim is on the bill as the person who made this link when it is the owner's, who left their name empty", () => {
    const answer = structuredClone(shareLocked) as { share: Record<string, unknown> };
    answer.share.matches = { "c-an": "p2", "c-minh": "me" };
    const page = mount(<ShareView state={stateOf(answer, { you: "c-minh" })} actions={actions()} />);
    expect(all(page, ".claimer").map((claimer) => textOf(claimer))).toEqual([
      "Minh You On the bill as the person who made this link",
      "An On the bill as An",
    ]);
    expect(textOf(page.querySelector(".person--you .person__row"))).toBe("The bill's owner You Shared this bill ₫77,000");
  });

  it("finds claims whose ids are __proto__ and constructor like any other", () => {
    const answer = structuredClone(shareLocked) as { share: Record<string, unknown> };
    answer.share.claims = [
      { id: "__proto__", name: "Proto", itemIds: ["i1"] },
      { id: "constructor", name: "Con", itemIds: ["i2"] },
    ];
    answer.share.matches = JSON.parse('{"__proto__": "p2"}') as unknown;
    const page = mount(<ShareView state={stateOf(answer, { you: "constructor" })} actions={actions()} />);
    expect(all(page, ".claimer").map((claimer) => textOf(claimer))).toEqual(["Proto On the bill as An", "Con You Not on the bill"]);
    expect(all(page, ".person--you")).toHaveLength(0);
  });

  it("lists people whose ids are __proto__ and constructor like anyone else", () => {
    const answer = structuredClone(shareLocked) as { share: Record<string, unknown> };
    answer.share.bill = { ...shareLocked.share.bill, participants: [{ id: "__proto__", name: "Proto" }, { id: "constructor", name: "Con" }], totalMinor: 3000 };
    answer.share.amounts = JSON.parse('{"__proto__": {"amountMinor": 1000, "payment": null}, "constructor": {"amountMinor": 2000, "payment": null}}') as unknown;
    const page = mount(<ShareView state={stateOf(answer)} actions={actions()} />);
    expect(all(page, ".person").map((person) => textOf(person))).toEqual([
      "Proto No way to pay was added: settle up directly with whoever paid the bill. ₫1,000",
      "Con No way to pay was added: settle up directly with whoever paid the bill. ₫2,000",
    ]);
  });

  it("says a link that does not exist does not, and one that was revoked no longer works", () => {
    const missing = mount(<ShareView state={loadingState({ phase: "failed", problem: { kind: "not_found" } })} actions={actions()} />);
    expect(textOf(missing.querySelector("h1"))).toBe("This link does not exist");
    expect(missing.querySelector("button")).toBeNull();
    const gone = mount(<ShareView state={loadingState({ phase: "failed", problem: { kind: "gone" } })} actions={actions()} />);
    expect(textOf(gone.querySelector("h1"))).toBe("This link no longer works");
    expect(textOf(gone)).toContain("turned the link off, or its 30 days are over");
  });

  it("offers to try again when the link could not be read", () => {
    const on = actions();
    const page = mount(<ShareView state={loadingState({ phase: "failed", problem: { kind: "network" } })} actions={on} />);
    expect(textOf(page.querySelector("h1"))).toBe("Split Vibe could not be reached");
    page.querySelector("button")?.click();
    expect(on.retry).toHaveBeenCalled();
  });

  it("says it is opening the bill until the first view comes", () => {
    const page = mount(<ShareView state={loadingState()} actions={actions()} />);
    expect(textOf(page.querySelector("h1"))).toBe("Opening the bill…");
  });

  it("announces what others did in a polite live region", () => {
    const page = mount(<ShareView state={stateOf(shareOpen, { announcement: { id: 1, text: "Minh picked Iced coffee." } })} actions={actions()} />);
    const region = page.querySelector('[aria-live="polite"]');
    expect(textOf(region)).toBe("Minh picked Iced coffee.");
  });

  it("says when the bill was updated and when the owner took this browser's picks off", () => {
    const on = actions();
    const page = mount(<ShareView state={stateOf(shareOpen, { billUpdated: true, removedByOwner: true })} actions={on} />);
    expect(textOf(page)).toContain("The bill was updated.");
    expect(textOf(page)).toContain("Lan took your picks off the bill.");
    Array.from(page.querySelectorAll("button"))
      .find((button) => textOf(button) === "OK")
      ?.click();
    expect(on.dismissBillUpdate).toHaveBeenCalled();
  });
});

describe("the colours of people on the bill link's page", () => {
  it("gives a person the colour the app gives the same name, by the hash Java gives it", () => {
    // String.hashCode in Java: 76153, 2398326, 2125, Integer.MIN_VALUE, -393679897 and 927946354, then Math.floorMod by 7.
    expect(["Lan", "Minh", "An", "polygenelubricants", "Nguyễn Thị Hồng", "😀 Kim"].map(personColorOf)).toEqual([0, 0, 4, 5, 5, 6]);
  });

  it("draws each other person's chip in their colour, and this browser's own in lime as You", () => {
    const chipsOf = (state: ShareState) =>
      all(all(mount(<ShareView state={state} actions={actions()} />), ".item")[0] ?? document.body, ".chip").map((chip) => [chip.className, textOf(chip)]);

    expect(chipsOf(stateOf(shareOpen))).toEqual([
      ["chip chip--person-0 type-label-medium", "Minh"],
      ["chip chip--person-4 type-label-medium", "An"],
    ]);
    expect(chipsOf(stateOf(shareOpen, { you: "c-minh", name: "Minh" }))).toEqual([
      ["chip chip--you type-label-medium", "You"],
      ["chip chip--person-4 type-label-medium", "An"],
    ]);
  });
});
