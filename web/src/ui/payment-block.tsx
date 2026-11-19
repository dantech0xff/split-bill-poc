import { createContext } from "preact";
import { useContext, useRef, useState } from "preact/hooks";
import type { Currency, PaymentPayload } from "../contract";
import { safePaymentLink } from "../links";
import { formatMoney } from "../money";
import { CopyIcon, OpenInNewIcon } from "./icons";
import { QrCode } from "./qr-code";

type CopyOutcome = "copied" | "selected" | "failed" | null;

/** Puts [text] on the clipboard, or else selects [element] so the person can copy it themself. */
export async function copyText(text: string, element: HTMLElement | null): Promise<Exclude<CopyOutcome, null>> {
  try {
    if (navigator.clipboard === undefined) throw new Error("No clipboard");
    await navigator.clipboard.writeText(text);
    return "copied";
  } catch {
    const selection = element === null ? null : window.getSelection();
    if (selection === null || element === null) return "failed";
    selection.selectAllChildren(element);
    return "selected";
  }
}

const COPY_MESSAGES: Record<Exclude<CopyOutcome, null>, string> = {
  copied: "Copied.",
  selected: "The details are selected: copy them from your browser's menu.",
  failed: "Copy the details by hand.",
};

interface PaymentBlockProps {
  payment: PaymentPayload;
  amountMinor: number;
  currency: Currency;
}

/** The page's "Report abuse" link, as `abuseMailto` makes it, or null while the Worker has no address for it. */
export const ReportAbuse = createContext<string | null>(null);

/**
 * How to pay one amount: a button for the payment link when it is one the page may open, the QR
 * code, and the details to copy. Everything is text the creditor's app wrote, shown as text, so
 * the block first says so, that Split Vibe does not check it, and where to report abuse.
 */
export function PaymentBlock({ payment, amountMinor, currency }: PaymentBlockProps) {
  const details = useRef<HTMLParagraphElement>(null);
  const [copy, setCopy] = useState<CopyOutcome>(null);
  const report = useContext(ReportAbuse);
  const link = safePaymentLink(payment.link);
  const amount = formatMoney(amountMinor, currency);

  const onCopy = async () => setCopy(await copyText(payment.details, details.current));

  return (
    <div class="payment">
      <p class="type-body-medium supporting">
        The person who sent this link wrote these payment details. Split Vibe does not check them.
        {report !== null && (
          <>
            {" "}
            <a href={report}>Report abuse</a>
          </>
        )}
      </p>
      {link !== null && (
        <a class="button button--filled" href={link.href} target="_blank" rel="noopener noreferrer">
          <OpenInNewIcon />
          <span>{link.kind === "upi" ? "Pay with a UPI app" : `Pay on ${link.host}`}</span>
        </a>
      )}
      {payment.qrPayload !== null && payment.qrPayload !== "" && (
        <figure class="qr-card">
          <QrCode payload={payment.qrPayload} label={`QR code to pay ${amount}`} />
          <figcaption class="type-body-medium">Scan it in your banking app.</figcaption>
        </figure>
      )}
      <div class="details-card">
        <p class="details-card__text type-body-large" ref={details}>
          {payment.details}
        </p>
        <button type="button" class="button button--text" onClick={onCopy}>
          <CopyIcon />
          <span>Copy the details</span>
        </button>
        <p class="type-body-small supporting" role="status">
          {copy === null ? "" : COPY_MESSAGES[copy]}
        </p>
      </div>
      <p class="type-body-medium supporting">
        {payment.amountFilledIn ? `The amount, ${amount}, is filled in for you.` : `Type in the amount yourself: ${amount}.`}
      </p>
    </div>
  );
}
