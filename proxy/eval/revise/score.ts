/**
 * The evaluation of edits by a sentence: its cases, how an answer is scored, and its report. The
 * runner, `run.mjs`, sends the cases through the proxy on a computer; the proxy's tests check the
 * case files and the scoring with the fake model. Only types are imported, so that Node runs this
 * file as it is, its types stripped.
 */
import type { Edit, Revision } from "../../src/split-revision";

/** The models the evaluation compares, each through the proxy on a computer. */
export const CANDIDATES = ["claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5"] as const;

/** What a case says about the instruction or the bill, beyond the edits it expects. */
export const TAGS = ["typed_differently", "adversarial"] as const;

/** A bill and its split, in the format of the request. */
export type Fixture = { bill: Record<string, unknown>; split: Record<string, unknown> };

/**
 * What a case expects: exactly these edits, and no question unless [mayAsk] allows one in place
 * of no edits; or a question, and no edits.
 */
export type Expectation = { edits: Edit[]; mayAsk: boolean } | { question: true };

export type EvalCase = { id: string; language: string; fixture: string; instruction: string; tags: string[]; expect: Expectation };

/** How one answer did against its case, and why it failed when it did. */
export type Score = { pass: boolean; reason: string | null };

/** One case as one model answered it, or the error that ended it. */
export type Outcome = {
  caseId: string;
  model: string;
  /** From sending the request to the line that ended the answer. */
  ms: number;
  /** What the call cost, in microdollars, as the proxy reported it; null when the model never answered. */
  costMicro: number | null;
  answer: Revision | null;
  error: string | null;
  score: Score;
};

/** The fixtures [value] holds, as `fixtures.json` writes them; throws at the first that is not one. */
export function fixturesOf(value: unknown): Record<string, Fixture> {
  if (!isObject(value)) throw new Error("fixtures.json must hold an object of fixtures by name");
  const fixtures: Record<string, Fixture> = {};
  for (const [name, fixture] of Object.entries(value)) {
    if (!isObject(fixture) || !isObject(fixture.bill) || !isObject(fixture.split)) throw new Error(`Fixture ${name} must have a bill and a split`);
    fixtures[name] = { bill: fixture.bill, split: fixture.split };
  }
  return fixtures;
}

/**
 * The cases [value] holds, as `cases.json` writes them, each of a fixture in [fixtures]; throws at
 * the first that is not one. Whether their edits follow the contract is the tests' to check, with
 * the proxy's own check of answers.
 */
export function casesOf(value: unknown, fixtures: Record<string, Fixture>): EvalCase[] {
  if (!Array.isArray(value)) throw new Error("cases.json must hold a list of cases");
  const ids = new Set<string>();
  return value.map((entry, index): EvalCase => {
    const where = `Case ${index + 1}`;
    if (!isObject(entry)) throw new Error(`${where} must be an object`);
    const { id, language, fixture, instruction, tags = [], expect } = entry;
    if (typeof id !== "string" || id === "" || ids.has(id)) throw new Error(`${where} needs an id of its own`);
    ids.add(id);
    if (typeof language !== "string" || !/^[a-z]{2}$/.test(language)) throw new Error(`${id} needs the code of its language`);
    if (typeof fixture !== "string" || !(fixture in fixtures)) throw new Error(`${id} names no fixture there is`);
    if (typeof instruction !== "string" || instruction.trim() === "") throw new Error(`${id} needs an instruction`);
    if (!Array.isArray(tags) || !tags.every((tag) => (TAGS as readonly unknown[]).includes(tag))) throw new Error(`${id} has a tag that is not one of ${TAGS.join(", ")}`);
    // Each tag was just found among TAGS, which are text.
    return { id, language, fixture, instruction, tags: tags as string[], expect: expectationOf(id, expect) };
  });
}

function expectationOf(id: string, value: unknown): Expectation {
  if (!isObject(value)) throw new Error(`${id} needs what it expects`);
  if (value.question === true && value.edits === undefined) return { question: true };
  if (!Array.isArray(value.edits) || value.question !== undefined) throw new Error(`${id} expects either edits or a question`);
  if (!value.edits.every((edit) => isObject(edit) && typeof edit.op === "string")) throw new Error(`${id} expects edits that each have an op`);
  if (value.mayAsk !== undefined && typeof value.mayAsk !== "boolean") throw new Error(`${id} says whether it may be asked with true or false`);
  if (value.mayAsk === true && value.edits.length > 0) throw new Error(`${id} may allow a question only in place of no edits`);
  // The fields of each edit are the proxy's tests' to check, through its own check of answers.
  return { edits: value.edits as Edit[], mayAsk: value.mayAsk === true };
}

/** The body the app would send for [evalCase], under [requestId]. */
export function requestOf(evalCase: EvalCase, fixtures: Record<string, Fixture>, requestId: string): Record<string, unknown> {
  const { bill, split } = fixtures[evalCase.fixture];
  return { requestId, instruction: evalCase.instruction, bill, split };
}

const PASS: Score = { pass: true, reason: null };

function fail(reason: string): Score {
  return { pass: false, reason };
}

/**
 * How [answer] did against [expect]. A case that expects a question passes when the answer asks
 * one, whatever it asks. Any other case passes when the answer asks nothing, or [Expectation.mayAsk]
 * allows it to, and its edits are the expected ones, compared as [editsMatch] says.
 */
export function scoreAnswer(expect: Expectation, answer: Revision): Score {
  if ("question" in expect) return answer.question !== null && answer.edits.length === 0 ? PASS : fail("edited instead of asking");
  if (answer.question !== null) return expect.mayAsk ? PASS : fail("asked instead of editing");
  return editsMatch(expect.edits, answer.edits);
}

/**
 * Whether [got] holds exactly the edits of [expected]:
 *
 * - Each edit must be one of the expected, field for field, and every expected edit must be there,
 *   as often as it is expected. Lists of people and the numbers per participant are compared as
 *   sets; the label of a new charge is not compared, as any short label will do; a new person's
 *   name is compared without regard to case; a quantity as a number ("4" is "4.0").
 * - The refs of new people may be given in another order: a ref the answer gives is read as the
 *   expected one of the person with the same name.
 * - Order is ignored, except between two edits that would come to something else the other way
 *   round, which [conflict] names; those must keep the expected order.
 */
export function editsMatch(expected: Edit[], got: Edit[]): Score {
  const renamed = withExpectedRefs(expected, got);
  const wanted = expected.map(canonical);
  const used = new Array<boolean>(expected.length).fill(false);
  const positions = new Array<number>(expected.length).fill(-1);
  const unexpected: string[] = [];
  renamed.forEach((edit, at) => {
    const key = canonical(edit);
    const index = wanted.findIndex((one, candidate) => !used[candidate] && one === key);
    if (index === -1) {
      unexpected.push(key);
    } else {
      used[index] = true;
      positions[index] = at;
    }
  });
  const missing = wanted.filter((_, index) => !used[index]);
  if (missing.length > 0 || unexpected.length > 0) {
    return fail([missing.length > 0 ? `missing ${missing.join(", ")}` : "", unexpected.length > 0 ? `unexpected ${unexpected.join(", ")}` : ""].filter(Boolean).join("; "));
  }
  for (let first = 0; first < expected.length; first++) {
    for (let second = first + 1; second < expected.length; second++) {
      if (conflict(expected[first], expected[second]) && positions[first] > positions[second]) {
        return fail(`${wanted[second]} came before ${wanted[first]}`);
      }
    }
  }
  return PASS;
}

/** [got] with each ref of a new person replaced by the expected ref of the person of the same name. */
function withExpectedRefs(expected: Edit[], got: Edit[]): Edit[] {
  const expectedRefs = new Map<string, string>();
  for (const edit of expected) if (edit.op === "add_person") expectedRefs.set(nameKey(edit.name), edit.ref);
  const refs = new Map<string, string>();
  for (const edit of got) {
    const ref = edit.op === "add_person" ? expectedRefs.get(nameKey(edit.name)) : undefined;
    if (edit.op === "add_person" && ref !== undefined) refs.set(edit.ref, ref);
  }
  const id = (participant: string) => refs.get(participant) ?? participant;
  const perPerson = (numbers: Record<string, number>) => Object.fromEntries(Object.entries(numbers).map(([participant, number]) => [id(participant), number]));
  return got.map((edit): Edit => {
    switch (edit.op) {
      case "add_person":
        return { ...edit, ref: id(edit.ref) };
      case "item_sharers":
        return { ...edit, participantIds: edit.participantIds.map(id) };
      case "item_shares":
      case "whole_shares":
        return { ...edit, shares: perPerson(edit.shares) };
      case "whole_percent":
        return { ...edit, basisPoints: perPerson(edit.basisPoints) };
      case "whole_amounts":
        return { ...edit, amounts: perPerson(edit.amounts) };
      case "remove_person":
      case "set_sharing":
      case "set_payer":
        return { ...edit, participantId: id(edit.participantId) };
      default:
        return edit;
    }
  });
}

/** [edit] written so that two edits that come to the same are the same text. */
function canonical(edit: Edit): string {
  switch (edit.op) {
    case "item_sharers":
      return sortedJson({ ...edit, participantIds: [...edit.participantIds].sort() });
    case "add_person":
      return sortedJson({ ...edit, name: nameKey(edit.name) });
    case "add_charge": {
      const { label: _label, ...charge } = edit;
      return sortedJson(charge);
    }
    case "item_quantity":
      return sortedJson({ ...edit, quantity: edit.quantity.includes(".") ? edit.quantity.replace(/\.?0+$/, "") : edit.quantity });
    default:
      return sortedJson(edit);
  }
}

function nameKey(name: string): string {
  return name.normalize("NFC").trim().toLowerCase();
}

/** JSON with the keys of every object sorted, so that the order of keys never counts. */
function sortedJson(value: unknown): string {
  return JSON.stringify(value, (_key, field: unknown) =>
    isObject(field) ? Object.fromEntries(Object.entries(field).sort(([one], [other]) => (one < other ? -1 : one > other ? 1 : 0))) : field,
  );
}

const WHOLE = new Set<string>(["split_mode", "whole_shares", "whole_percent", "whole_amounts"]);
const ITEM_SPLIT = new Set<string>(["item_sharers", "item_shares"]);
const ITEM_VALUE = new Set<string>(["item_amount", "item_quantity"]);

/**
 * Whether [one] and [other] come to something else when they swap places, as the app applies
 * edits in order:
 *
 * - two edits of how the whole bill is split, or one of those and an edit of how an item is split,
 *   since an edit of an item switches the bill to splitting by item;
 * - two edits of the same item;
 * - adding or removing a person, and an edit that names them;
 * - a charge by percentage, which is worked out from the items, and an edit of an item's amount or
 *   quantity;
 * - the exact amounts of the whole bill, which add up to its total, and a new charge or an edit of
 *   an item's amount or quantity, which can change it.
 */
export function conflict(one: Edit, other: Edit): boolean {
  const pair = (test: (first: Edit, second: Edit) => boolean) => test(one, other) || test(other, one);
  if (pair((first, second) => WHOLE.has(first.op) && (WHOLE.has(second.op) || ITEM_SPLIT.has(second.op)))) return true;
  if ("itemId" in one && "itemId" in other && one.itemId === other.itemId) return true;
  if (pair((first, second) => first.op === "add_person" && peopleOf(second).includes(first.ref))) return true;
  if (pair((first, second) => first.op === "remove_person" && peopleOf(second).includes(first.participantId))) return true;
  if (pair((first, second) => first.op === "add_charge" && "percentBasisPoints" in first && ITEM_VALUE.has(second.op))) return true;
  return pair((first, second) => first.op === "whole_amounts" && (second.op === "add_charge" || ITEM_VALUE.has(second.op)));
}

/** The participants [edit] names, apart from the person it adds. */
function peopleOf(edit: Edit): string[] {
  switch (edit.op) {
    case "item_sharers":
      return edit.participantIds;
    case "item_shares":
    case "whole_shares":
      return Object.keys(edit.shares);
    case "whole_percent":
      return Object.keys(edit.basisPoints);
    case "whole_amounts":
      return Object.keys(edit.amounts);
    case "remove_person":
    case "set_sharing":
    case "set_payer":
      return [edit.participantId];
    default:
      return [];
  }
}

/** The value at the [percent]th percentile of [values], by nearest rank; null for no values. */
export function percentile(values: number[], percent: number): number | null {
  if (values.length === 0) return null;
  const sorted = [...values].sort((one, other) => one - other);
  return sorted[Math.max(0, Math.ceil((percent / 100) * sorted.length) - 1)];
}

/** What one model did over the cases it was sent. */
export type ModelSummary = {
  model: string;
  cases: number;
  passed: number;
  /** Calls that ended without an answer to score: an error line, or a request that failed. */
  errors: number;
  p50Ms: number | null;
  p95Ms: number | null;
  /** Over the calls the proxy reported a cost for, in microdollars. */
  averageCostMicro: number | null;
  /** Cases and passes by language. */
  languages: Record<string, { cases: number; passed: number }>;
};

/** [outcomes] summed up by model, in the order of [models]. */
export function summaryOf(outcomes: Outcome[], cases: EvalCase[], models: readonly string[]): ModelSummary[] {
  const languageOf = new Map(cases.map((evalCase) => [evalCase.id, evalCase.language]));
  return models.map((model) => {
    const mine = outcomes.filter((outcome) => outcome.model === model);
    const costs = mine.flatMap((outcome) => (outcome.costMicro === null ? [] : [outcome.costMicro]));
    const languages: ModelSummary["languages"] = {};
    for (const outcome of mine) {
      const language = languageOf.get(outcome.caseId) ?? "?";
      languages[language] ??= { cases: 0, passed: 0 };
      languages[language].cases += 1;
      if (outcome.score.pass) languages[language].passed += 1;
    }
    return {
      model,
      cases: mine.length,
      passed: mine.filter((outcome) => outcome.score.pass).length,
      errors: mine.filter((outcome) => outcome.error !== null).length,
      p50Ms: percentile(mine.map((outcome) => outcome.ms), 50),
      p95Ms: percentile(mine.map((outcome) => outcome.ms), 95),
      averageCostMicro: costs.length === 0 ? null : costs.reduce((sum, cost) => sum + cost, 0) / costs.length,
      languages,
    };
  });
}

/** What a run of the evaluation tells, for its report. */
export type Run = { startedAt: Date; commit: string; models: readonly string[]; cases: EvalCase[]; outcomes: Outcome[] };

/** The report of [run], in Markdown, as `run.mjs` writes it to `plans/reports/`. */
export function reportOf(run: Run): string {
  const summaries = summaryOf(run.outcomes, run.cases, run.models);
  const languages = [...new Set(run.cases.map((evalCase) => evalCase.language))];
  const byId = new Map(run.cases.map((evalCase) => [evalCase.id, evalCase]));
  const lines = [
    "# Evaluation of edits by a sentence",
    "",
    `Run on ${run.startedAt.toISOString().slice(0, 16).replace("T", " ")} UTC at commit \`${run.commit}\`, through the proxy on a computer, with the prompt, schema and settings of \`proxy/src\`: one call per case and model, ${run.cases.length} cases in ${languages.join(", ")}. The cases are in \`proxy/eval/revise/cases.json\`.`,
    "",
    "A case that expects a question passes when the answer asks one. Any other passes when the answer's edits are exactly the expected ones and it asks nothing, though a case that expects no edits may allow a question instead. Edits are compared with lists of people and numbers per participant as sets, without the label of a new charge, with a new person's ref matched by name, and in any order but between two edits that come to something else the other way round. An answer that ends in an error fails.",
    "",
    "| Model | Passed | Errors | p50 | p95 | Average cost per call |",
    "|---|---|---|---|---|---|",
    ...summaries.map(
      (summary) =>
        `| ${summary.model} | ${summary.passed} of ${summary.cases} (${percent(summary.passed, summary.cases)}) | ${summary.errors} | ${seconds(summary.p50Ms)} | ${seconds(summary.p95Ms)} | ${dollars(summary.averageCostMicro)} |`,
    ),
    "",
    "## By language",
    "",
    `| Model | ${languages.join(" | ")} |`,
    `|---|${languages.map(() => "---").join("|")}|`,
    ...summaries.map((summary) => `| ${summary.model} | ${languages.map((language) => passedOf(summary.languages[language])).join(" | ")} |`),
    "",
    "## Cases that failed",
  ];
  for (const model of run.models) {
    const failed = run.outcomes.filter((outcome) => outcome.model === model && !outcome.score.pass);
    lines.push("", `### ${model}`, "");
    if (failed.length === 0) lines.push("None.");
    for (const outcome of failed) {
      const evalCase = byId.get(outcome.caseId);
      const asked = outcome.answer === null ? null : outcome.answer.question;
      const got = outcome.error !== null ? "" : ` Got ${asked !== null ? `the question ${JSON.stringify(asked)}` : JSON.stringify(outcome.answer?.edits ?? [])}.`;
      lines.push(`- \`${outcome.caseId}\` (${evalCase?.language ?? "?"}) ${JSON.stringify(evalCase?.instruction ?? "")}: ${outcome.score.reason ?? "failed"}.${got}`);
    }
  }
  return `${lines.join("\n")}\n`;
}

function percent(part: number, whole: number): string {
  return whole === 0 ? "-" : `${((part / whole) * 100).toFixed(1)}%`;
}

function passedOf(tally: { cases: number; passed: number } | undefined): string {
  return tally === undefined ? "-" : `${tally.passed} of ${tally.cases}`;
}

function seconds(ms: number | null): string {
  return ms === null ? "-" : `${(ms / 1000).toFixed(1)} s`;
}

function dollars(micro: number | null): string {
  return micro === null ? "-" : `$${(micro / 1_000_000).toFixed(5)}`;
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}
