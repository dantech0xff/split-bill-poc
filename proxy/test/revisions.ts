import { POLICY } from "../src/policy";
import type { Edit, Revision } from "../src/split-revision";
import type { Reply } from "./fake-model";
import { type Caller, proxyRequest } from "./requests";

/** The body of an edit, as the app sends it. */
export type RevisionBody = { requestId: string; instruction: string; bill: Record<string, unknown>; split: Record<string, unknown> };

/** The contract's example of an edit, with a fresh request id each time. */
export function sampleRevision(): RevisionBody {
  return {
    requestId: crypto.randomUUID(),
    instruction: "Lan không uống bia",
    bill: {
      merchant: "Pho 24",
      currency: { code: "VND", fractionDigits: 0 },
      items: [{ id: "i1", name: "Bia", quantity: "3", amountMinor: 90000 }],
      charges: [{ id: "c1", label: "Service charge", kind: "service_charge", amountMinor: 14000 }],
      totalMinor: 154000,
    },
    split: {
      participants: [
        { id: "me", name: "Dana" },
        { id: "p1", name: "Lan" },
      ],
      whole: null,
      items: { i1: { type: "equal", among: ["me"], except: [] } },
      notSharing: [],
      payments: [{ participantId: "me", amountMinor: 154000 }],
    },
  };
}

/** The contract's example of an answer. */
export const SAMPLE_REVISION: Revision = {
  edits: [{ op: "item_sharers", itemId: "i1", participantIds: ["me"] }],
  explanation: "Lan no longer shares the beer.",
  question: null,
};

/** An edit of the sample split, unless [options] gives another body or a raw one. */
export function revisionRequest(options: Caller & { body?: unknown; raw?: string } = {}): Request {
  const body = options.raw ?? JSON.stringify(options.body ?? sampleRevision());
  return proxyRequest("POST", "/v1/split-revision", options, body);
}

/** [edit] as the model writes it: each number per participant as a pair, since its schema cannot key an object by them. */
export function modelEdit(edit: Edit): Record<string, unknown> {
  const pairs = (numbers: Record<string, number>, field: string) => Object.entries(numbers).map(([participantId, number]) => ({ participantId, [field]: number }));
  switch (edit.op) {
    case "item_shares":
    case "whole_shares":
      return { ...edit, shares: pairs(edit.shares, "shares") };
    case "whole_percent":
      return { ...edit, basisPoints: pairs(edit.basisPoints, "basisPoints") };
    case "whole_amounts":
      return { ...edit, amounts: pairs(edit.amounts, "amountMinor") };
    default:
      return edit;
  }
}

/** The model's answer that the proxy turns into [revision], as the model streams it; [written] replaces any of its fields. */
export function modelRevision(revision: Revision, written: Record<string, unknown> = {}): Reply {
  return {
    text: JSON.stringify({ question: revision.question, edits: revision.edits.map(modelEdit), explanation: revision.explanation, ...written }),
    model: POLICY.revisionModel,
    usage: { input_tokens: 3_000, output_tokens: 400 },
  };
}
