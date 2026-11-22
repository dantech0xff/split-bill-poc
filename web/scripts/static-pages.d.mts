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
export const HOME_FILE: string;

/** A screenshot of the home page: its name in src/landing/, where it is a light and a `-dark` file, and what it shows. */
export interface LandingImage {
  name: string;
  alt: string;
}

/** Where the home page's screenshots are kept. */
export const LANDING_DIRECTORY: string;
export const LANDING_IMAGES: readonly LandingImage[];
/** The size of every screenshot in pixels. */
export const LANDING_IMAGE_SIZE: { readonly width: number; readonly height: number };
/** What the home page says about getting the app while it is not on Google Play yet. */
export const GET_THE_APP: string;
/** The paths under /assets/ of each screenshot's light and dark file, by its name. */
export type LandingShots = Readonly<Record<string, { readonly light: string; readonly dark: string }>>;

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
/** The home page, with [stylesheet], the stylesheet tag of index.html, and the screenshots' files in [shots]. */
export function homePage(stylesheet: string, shots: LandingShots): string;
/** Copies the screenshots from [source] into [output]/assets/ under hashed names, and gives their paths by name. */
export function copyLandingImages(source?: string, output?: string): Promise<LandingShots>;
/** Writes the legal pages, the page /delete-account and the home page with its screenshots from [landing] next to the index.html in [output]. */
export function writeStaticPages(docs?: string, output?: string, landing?: string): Promise<void>;
