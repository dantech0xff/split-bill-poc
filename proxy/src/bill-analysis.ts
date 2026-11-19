import { WEIGHTS } from "./identity";
import type { Env } from "./index";
import { type BillRequest, billRequestOf } from "./input";
import { callModel, type ModelChoice, type ModelTask, modelOf, TIMING, type Timing } from "./model-call";
import { POLICY } from "./policy";
import { SYSTEM_PROMPT, userMessage } from "./prompt";
import { BILL_SCHEMA } from "./schema";

/** A bill read and how its model is asked: the policy's model, or the one the evaluation names. */
type BillCall = BillRequest & ModelChoice;

/**
 * A bill read: the model reads the text the app recognized on a bill, with what the user typed,
 * and answers as [BILL_SCHEMA] shapes it. The app's `BillAnalysisParser` reads the answer as the
 * model wrote it, so it goes out unchanged, and it is charged whatever it says. The model is
 * [POLICY.model], or on a computer the one the evaluation of bill reads names, as [modelOf] says.
 */
const BILL_READ: ModelTask<BillCall> = {
  event: "bill_analysis",
  weight: WEIGHTS.billAnalysis,
  maxBodyBytes: POLICY.maxBodyBytes,
  inputOf(body, request) {
    const read = billRequestOf(body);
    const model = modelOf(request, POLICY.model);
    return read === null || model === null ? null : { ...read, ...model };
  },
  system: SYSTEM_PROMPT,
  schema: BILL_SCHEMA,
  userMessage: (input) => userMessage(input.ocrDocument, input.instruction, input.localCurrency),
  settings: (call) => ({ model: call.model, maxTokens: POLICY.maxTokens, effort: POLICY.effort, fallback: true, report: call.report }),
  answerOf: (value) => value,
  chargeBadAnswer: true,
};

/** `POST /v1/bill-analysis`: reads one bill with the model, as [callModel] does every call. */
export function billAnalysis(request: Request, env: Env, ctx: ExecutionContext, timing: Timing = TIMING): Promise<Response> {
  return callModel(request, env, ctx, BILL_READ, timing);
}
