/** The kinds of page the Worker serves: the link pages, /delete-account, and the legal pages. */
export type PageKind = "link" | "delete-account" | "legal";

/** The pages `npm run build` writes, each with the kind of checks it passes. */
export const PAGE_FILES: ReadonlyArray<{ file: string; kind: PageKind }>;

/** The kinds of file under assets/, by their ending, with the content type the Worker serves each with. */
export const ASSET_TYPES: Readonly<Record<".js" | ".css" | ".woff2", string>>;

/** The content type of [name], a file under assets/, or null when the Worker serves no such file. */
export function assetType(name: string): string | null;

/** The addresses [css] loads files from, those of its `url(...)`s, in order. */
export function stylesheetUrls(css: string): string[];

/** What is wrong with [css], a stylesheet under assets/, given the names of the files there, one sentence for each; empty when nothing is. */
export function stylesheetProblems(css: string, assets: ReadonlySet<string>): string[];

/** What is wrong with [html] as a page of [kind] that the Worker serves, one sentence for each; empty when nothing is. */
export function pageProblems(html: string, kind?: PageKind): string[];
