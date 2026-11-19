/**
 * Receipts as a till prints them and a phone photographs them: rows of text on paper a number of
 * characters wide, turned into the lines a recognizer returns, each with the four corners of its
 * box in pixels, which `ocr-layout.ts` then writes as the app does. A photo can be turned, show the
 * paper curled, place every box a little off, misread or lose some pieces, cut the bottom off, or
 * come in several pages that overlap by a row. Everything is worked out from the bill's id, so a
 * bill always comes out the same. Node runs this file as it is, its types stripped.
 */
import type { OcrLine, OcrPage, OcrPoint } from "./ocr-layout.ts";

/** A piece of text printed on a row: from column [at] when left-aligned, up to column [at] when right-aligned, or in the middle of the paper; [size] 2 is double width and height. */
export type Piece = { text: string; at: number; align: "left" | "right" | "center"; size: 1 | 2 };

/** A printed row, or none for a row left blank. */
export type PrintedRow = Piece[];

/** How the receipt was photographed; a photo without any of these is upright, flat and whole. */
export type Photo = {
  /** Degrees the paper is turned by, clockwise. */
  tilt?: number;
  /** Pixels by which the middle of every row sits above its ends, as on paper that curls. */
  curl?: number;
  /** The most pixels a box lies off where it was printed, up or down and left or right: 2 unless said. */
  jitter?: number;
  /** Pieces the recognizer read wrong, by the text printed: what it read instead, or "" for a piece it lost. */
  misread?: Record<string, string>;
  /** Rows at the bottom the photo left out. */
  cut?: number;
  /** The receipt photographed in parts of these many rows, each part after the first starting again with the last row of the one before. */
  pages?: number[];
  /** Degrees each part of [pages] is turned by. */
  pageTilts?: number[];
};

const CHAR_WIDTH = 21;
const TEXT_HEIGHT = 26;
const ROW_PITCH = 46;
const MARGIN = 36;

export function left(at: number, text: string): Piece {
  return { text, at, align: "left", size: 1 };
}

export function right(at: number, text: string): Piece {
  return { text, at, align: "right", size: 1 };
}

export function center(text: string, size: 1 | 2 = 1): Piece {
  return { text, at: 0, align: "center", size };
}

/** A row of dashes across paper [columns] wide, as tills print between parts of a receipt. */
export function rule(columns: number, mark = "-"): PrintedRow {
  return [left(0, mark.repeat(columns))];
}

/**
 * The pages a phone reads from [rows] printed on paper [columns] characters wide, photographed as
 * [photo] says. Throws when a piece runs off the paper or touches another, which no till prints.
 */
export function photograph(rows: PrintedRow[], columns: number, photo: Photo, seed: string): OcrPage[] {
  rows.forEach((row, index) => {
    const spans = row.map((piece) => ({ piece, ...spanOf(piece, columns) })).sort((one, other) => one.start - other.start);
    spans.forEach((span, at) => {
      if (span.start < 0 || span.end > columns || (at > 0 && span.start <= spans[at - 1].end)) {
        throw new Error(`Row ${index + 1} of ${seed} prints ${JSON.stringify(span.piece.text)} off the paper or against another piece`);
      }
    });
  });
  const random = randomOf(seed);
  const kept = rows.slice(0, rows.length - (photo.cut ?? 0));
  const parts: PrintedRow[][] = [];
  if (photo.pages === undefined) {
    parts.push(kept);
  } else {
    let start = 0;
    for (const [index, count] of photo.pages.entries()) {
      parts.push(kept.slice(start, start + count));
      start += count - (index < photo.pages.length - 1 ? 1 : 0);
    }
    if (start < kept.length) throw new Error(`The pages of ${seed} leave ${kept.length - start} rows out`);
  }
  return parts.map((part, index) => page(part, columns, { ...photo, tilt: photo.pageTilts?.[index] ?? photo.tilt }, random));
}

/** Every text [rows] print, for the check that a bill prints what it expects. */
export function textsOf(rows: PrintedRow[]): string[] {
  return rows.flatMap((row) => row.map((piece) => piece.text));
}

/** The columns [piece] covers on paper [columns] wide, from [start] up to [end]. */
function spanOf(piece: Piece, columns: number): { start: number; end: number } {
  const length = [...piece.text.normalize("NFC")].length * piece.size;
  const start = piece.align === "left" ? piece.at : piece.align === "right" ? piece.at - length : (columns - length) / 2;
  return { start, end: start + length };
}

function page(rows: PrintedRow[], columns: number, photo: Photo, random: () => number): OcrPage {
  const width = MARGIN * 2 + columns * CHAR_WIDTH;
  const jitter = photo.jitter ?? 2;
  const lines: OcrLine[] = [];
  let top = MARGIN;
  for (const row of rows) {
    const tallest = Math.max(1, ...row.map((piece) => piece.size));
    for (const piece of row) {
      const text = photo.misread !== undefined && Object.hasOwn(photo.misread, piece.text) ? photo.misread[piece.text] : piece.text;
      const span = spanOf(piece, columns);
      const shift = () => Math.round((random() * 2 - 1) * jitter);
      const x1 = MARGIN + span.start * CHAR_WIDTH + shift();
      const x2 = MARGIN + span.end * CHAR_WIDTH + shift();
      const y1 = top + (tallest - piece.size) * TEXT_HEIGHT + shift();
      const y2 = y1 + TEXT_HEIGHT * piece.size + shift();
      if (text !== "") lines.push({ text, corners: [point(x1, y1), point(x2, y1), point(x2, y2), point(x1, y2)] });
    }
    top += ROW_PITCH + (tallest - 1) * TEXT_HEIGHT;
  }
  const height = top + MARGIN;
  const curled = (corner: OcrPoint): OcrPoint => {
    const across = Math.min(Math.max((corner.x - MARGIN) / (width - MARGIN * 2), 0), 1);
    return { x: corner.x, y: corner.y - (photo.curl ?? 0) * 4 * across * (1 - across) };
  };
  const turn = (photo.tilt ?? 0) * (Math.PI / 180);
  const turned = (corner: OcrPoint): OcrPoint => {
    const dx = corner.x - width / 2;
    const dy = corner.y - height / 2;
    return point(width / 2 + dx * Math.cos(turn) - dy * Math.sin(turn), height / 2 + dx * Math.sin(turn) + dy * Math.cos(turn));
  };
  return {
    imageWidth: Math.round(width),
    imageHeight: Math.round(height),
    lines: lines.map((line) => ({ text: line.text, corners: line.corners.map((corner) => turned(curled(corner))) })),
  };
}

/** A corner in whole pixels, as a recognizer gives it. */
function point(x: number, y: number): OcrPoint {
  return { x: Math.round(x), y: Math.round(y) };
}

/** A generator of numbers from 0 to 1 that always gives the same ones for [seed] (mulberry32). */
function randomOf(seed: string): () => number {
  let state = [...seed].reduce((hash, char) => Math.imul(hash ^ (char.codePointAt(0) ?? 0), 16_777_619) >>> 0, 2_166_136_261);
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let value = state;
    value = Math.imul(value ^ (value >>> 15), value | 1);
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61);
    return ((value ^ (value >>> 14)) >>> 0) / 4_294_967_296;
  };
}
