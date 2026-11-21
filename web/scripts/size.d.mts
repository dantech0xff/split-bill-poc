/** The most the page's HTML, script and styles may weigh gzipped, in bytes; reaching it fails. */
export const BUDGET_BYTES: number;
/** The most the fonts may weigh together, in bytes, apart from [BUDGET_BYTES]. */
export const FONT_CAP_BYTES: number;
/** The most font files the pages may load. */
export const MAX_FONTS: number;

/** The page's initial download: each file with its size and its size gzipped, and each font its stylesheet loads, with its size. */
export interface Download {
  page: ReadonlyArray<{ name: string; raw: number; gzip: number }>;
  fonts: ReadonlyArray<{ name: string; bytes: number }>;
}

/** The initial download of the page built into [directory], web/dist unless given. */
export function downloadOf(directory?: string): Download;
/** What in [download] is over its limit, one sentence for each; empty when nothing is. */
export function sizeProblems(download: Download): string[];
/** The lines `npm run size` prints for [download]. */
export function sizeReport(download: Download): string[];
