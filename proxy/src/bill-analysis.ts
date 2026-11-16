import { WEIGHTS } from "./identity";
import type { Env } from "./index";
import { type BillRequest, billRequestOf } from "./input";
import { callModel, type ModelTask, TIMING, type Timing } from "./model-call";
import { POLICY } from "./policy";
import { SYSTEM_PROMPT, userMessage } from "./prompt";
import { BILL_SCHEMA } from "./schema";

/**
 * A bill read: the model reads the text the app recognized on a bill, with what the user typed,
 * and answers as [BILL_SCHEMA] shapes it. The app's `BillAnalysisParser` reads the answer as the
 * model wrote it, so it goes out unchanged, and it is charged whatever it says.
 */
const BILL_READ: ModelTask<BillRequest> = {
  event: "bill_analysis",
  weight: WEIGHTS.billAnalysis,
  maxBodyBytes: POLICY.maxBodyBytes,
  inputOf: billRequestOf,
  system: SYSTEM_PROMPT,
  schema: BILL_SCHEMA,
  userMessage: (input) => userMessage(input.ocrDocument, input.instruction, input.localCurrency),
  settings: () => ({ model: POLICY.model, maxTokens: POLICY.maxTokens, effort: POLICY.effort, fallback: true, report: false }),
  answerOf: (value) => value,
  chargeBadAnswer: true,
};

/** `POST /v1/bill-analysis`: reads one bill with the model, as [callModel] does every call. */
export function billAnalysis(request: Request, env: Env, ctx: ExecutionContext, timing: Timing = TIMING): Promise<Response> {
  return callModel(request, env, ctx, BILL_READ, timing);
}
