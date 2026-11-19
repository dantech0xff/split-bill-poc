/** The kinds of page the Worker serves: the link pages, /delete-account, and the legal pages. */
export type PageKind = "link" | "delete-account" | "legal";

/** The pages `npm run build` writes, each with the kind of checks it passes. */
export const PAGE_FILES: ReadonlyArray<{ file: string; kind: PageKind }>;

/** What is wrong with [html] as a page of [kind] that the Worker serves, one sentence for each; empty when nothing is. */
export function pageProblems(html: string, kind?: PageKind): string[];
