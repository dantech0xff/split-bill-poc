/** A legal page: the file written, its source in docs/, its path on the Worker, and its title. */
export interface LegalPage {
  file: string;
  source: string;
  path: string;
  title: string;
}

export const DOCS_DIRECTORY: string;
export const LEGAL_PAGES: readonly LegalPage[];
export const SITE_LINKS: ReadonlyArray<{ path: string; label: string }>;
export const DELETE_ACCOUNT_FILE: string;

export class StaticPageError extends Error {}

/** [markdown], the content of docs/[source], as the HTML of a legal page. */
export function renderLegalMarkdown(markdown: string, source: string): string;
/** The legal [page] with [content], its rendered Markdown, and [stylesheet], the stylesheet tag of index.html. */
export function legalPage(page: LegalPage, content: string, stylesheet: string): string;
/** The page /delete-account, with the web app's [script] and [stylesheet] tags. */
export function deleteAccountPage(script: string, stylesheet: string): string;
/** The tags of index.html that load the web app's one script and one stylesheet. */
export function assetTagsOf(index: string): { script: string; stylesheet: string };
/** The sources of the legal pages that [docs] lacks, as `docs/<name>`. */
export function missingSources(docs: string): string[];
/** Writes the legal pages and the page /delete-account next to the index.html in [output]. */
export function writeStaticPages(docs?: string, output?: string): Promise<void>;
