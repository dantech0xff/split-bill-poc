/**
 * The proxy that the evaluation of bill reads runs on a computer, as `run.mjs` starts it with
 * `wrangler dev eval/read/worker.ts`: the Worker of `src/index.ts` with its bindings, but for a
 * bill read that names its model in X-SplitVibe-Eval-Model, the header the edit route reads for
 * its own evaluation. The app's route always asks `POLICY.model` and reports nothing of the call,
 * so such a read is asked here of the model it names, through the same call to the model with the
 * route's contract check, prompt, schema, settings, limits and charges; and its last line also
 * reports the model, its tokens and the cost, as `eval`. The header counts only when the proxy is
 * asked at a computer's hostname, and only for a model with a price in `spend.ts`; another model
 * is refused as input. Nothing deploys this file: `wrangler.jsonc` names `src/index.ts`. The
 * tests check that such a read sends the model what the app's route sends, but the model's name,
 * and ends with the route's own lines.
 */
import { WEIGHTS } from "../../src/identity";
import worker, { type Env } from "../../src/index";
import { type BillRequest, billRequestOf } from "../../src/input";
import { callModel, type ModelTask, TIMING } from "../../src/model-call";
import { POLICY } from "../../src/policy";
import { SYSTEM_PROMPT, userMessage } from "../../src/prompt";
import { errorResponse } from "../../src/respond";
import { BILL_SCHEMA } from "../../src/schema";
import { isPriced } from "../../src/spend";
import { EVAL_MODEL_HEADER } from "../../src/split-revision";
import { servedLocally } from "../../src/switches";

export { AbuseLedger, FxBook, QuotaBook, ShareRoom } from "../../src/index";

/** A bill read and the model the evaluation names for it. */
type NamedRead = BillRequest & { model: string };

/** The bill read of `src/bill-analysis.ts`, asked of the named model, its cost reported. */
const NAMED_READ: ModelTask<NamedRead> = {
  event: "bill_analysis",
  weight: WEIGHTS.billAnalysis,
  maxBodyBytes: POLICY.maxBodyBytes,
  inputOf(body, request) {
    const read = billRequestOf(body);
    const model = request.headers.get(EVAL_MODEL_HEADER)?.trim() ?? "";
    return read === null || !isPriced(model) ? null : { ...read, model };
  },
  system: SYSTEM_PROMPT,
  schema: BILL_SCHEMA,
  userMessage: (input) => userMessage(input.ocrDocument, input.instruction, input.localCurrency),
  settings: (input) => ({ model: input.model, maxTokens: POLICY.maxTokens, effort: POLICY.effort, fallback: true, report: true }),
  answerOf: (value) => value,
  chargeBadAnswer: true,
};

export default {
  async fetch(request, env, ctx): Promise<Response> {
    const url = new URL(request.url);
    const named = request.method === "POST" && url.pathname === "/v1/bill-analysis" && request.headers.has(EVAL_MODEL_HEADER) && servedLocally(url.hostname);
    if (!named) return worker.fetch(request, env, ctx);
    try {
      return await callModel(request, env, ctx, NAMED_READ, TIMING);
    } catch (error) {
      console.error(JSON.stringify({ event: "request_failed", error: error instanceof Error ? error.name : "unknown" }));
      return errorResponse(500, "internal");
    }
  },
  scheduled: worker.scheduled,
} satisfies ExportedHandler<Env>;
