/**
 * The app's `OcrLayoutEncoder` (`core/domain/.../ocr/OcrLayoutEncoder.kt`), written again so that
 * the evaluation sends each bill as the phone would: it straightens the page by the direction its
 * text runs in, groups the text into rows, and writes every fragment with its box on a scale of 0
 * to 1000. The app does that arithmetic in 32-bit floats, so this rounds to them where the app does,
 * and writes the same document for the same lines; the tests hold it to the app's own fixture.
 * Node runs this file as it is, its types stripped.
 */

/** A corner of a line's box, in pixels of the photo. */
export type OcrPoint = { x: number; y: number };

/** One line the phone's recognizer found: its text, and the four corners of its box, clockwise from the text's own top-left. */
export type OcrLine = { text: string; corners: OcrPoint[] };

/** Everything the recognizer found in one photo. */
export type OcrPage = { imageWidth: number; imageHeight: number; lines: OcrLine[] };

type Fragment = { text: string; x1: number; y1: number; x2: number; y2: number };

type Box = { text: string; left: number; top: number; right: number; bottom: number; centerY: number; height: number };

const SCALE = 1000;
const ROW_TOLERANCE = 0.5;
/** A value as a 32-bit float holds it, which is where the app keeps its coordinates. */
const float = Math.fround;
/** What Kotlin's `Char.isWhitespace` takes for a space: Java's whitespace and space characters. */
const SPACE = "[\\t\\n\\u000B\\f\\r\\u001C-\\u001F\\p{Zs}\\u2028\\u2029]";
const BLANK = new RegExp(`^${SPACE}*$`, "u");
const EDGE_SPACES = new RegExp(`^${SPACE}+|${SPACE}+$`, "gu");

/**
 * The document of a bill photographed in [pages], in order, as the app sends it in `ocrDocument`:
 * `<bill_ocr rows="N">` with the rows of a single page, or `<bill_ocr pages="N">` with one
 * `<page n="…" rows="…">` per photo, rows numbered and positions measured on each page by itself.
 */
export function encodePages(pages: OcrPage[]): string {
  let document = "";
  if (pages.length === 1) {
    const rows = rowsOf(pages[0]);
    document += `<bill_ocr rows="${rows.length}">\n${rowsText(rows)}`;
  } else {
    document += `<bill_ocr pages="${pages.length}">\n`;
    pages.forEach((page, index) => {
      const rows = rowsOf(page);
      document += `<page n="${index + 1}" rows="${rows.length}">\n${rowsText(rows)}</page>\n`;
    });
  }
  return `${document}</bill_ocr>`;
}

/** The rows of [page], top to bottom, each with its fragments left to right. */
export function rowsOf(page: OcrPage): Fragment[][] {
  const lines = page.lines.filter((line) => !BLANK.test(line.text));
  if (lines.length === 0) return [];
  const skew = dominantAngle(lines);
  const boxes = lines.map((line) => straighten(line, skew));
  const minX = Math.min(...boxes.map((box) => box.left));
  const maxX = Math.max(...boxes.map((box) => box.right));
  const minY = Math.min(...boxes.map((box) => box.top));
  const maxY = Math.max(...boxes.map((box) => box.bottom));
  const width = Math.max(float(maxX - minX), 1);
  const height = Math.max(float(maxY - minY), 1);
  return groupIntoRows(boxes).map((row) =>
    [...row]
      .sort((one, other) => one.left - other.left)
      .map((box) => ({
        text: box.text,
        x1: scale(float(box.left - minX), width),
        y1: scale(float(box.top - minY), height),
        x2: scale(float(box.right - minX), width),
        y2: scale(float(box.bottom - minY), height),
      })),
  );
}

function rowsText(rows: Fragment[][]): string {
  return rows
    .map((row, index) => `${index + 1}${row.map((fragment) => ` | [${fragment.x1},${fragment.y1},${fragment.x2},${fragment.y2}] ${quote(fragment.text)}`).join("")}\n`)
    .join("");
}

/** The direction the text runs in, in radians: the mean of the lines' top edges, each weighted by its length. */
function dominantAngle(lines: OcrLine[]): number {
  let sumCos = 0;
  let sumSin = 0;
  for (const line of lines) {
    const [topLeft, topRight] = line.corners;
    sumCos += float(float(topRight.x) - float(topLeft.x));
    sumSin += float(float(topRight.y) - float(topLeft.y));
  }
  if (sumSin === 0 && sumCos === 0) return 0;
  return Math.atan2(sumSin, sumCos);
}

/** [line] turned by -[angle], so that its text runs left to right, as an upright box. */
function straighten(line: OcrLine, angle: number): Box {
  const cos = Math.cos(angle);
  const sin = Math.sin(angle);
  const xs = line.corners.map((point) => float(float(point.x) * cos + float(point.y) * sin));
  const ys = line.corners.map((point) => float(-float(point.x) * sin + float(point.y) * cos));
  const top = Math.min(...ys);
  const bottom = Math.max(...ys);
  return {
    text: line.text.replace(EDGE_SPACES, ""),
    left: Math.min(...xs),
    top,
    right: Math.max(...xs),
    bottom,
    centerY: float(float(top + bottom) / 2),
    height: float(bottom - top),
  };
}

/** Boxes whose vertical centers are within half a line height of a row's, in rows top to bottom. */
function groupIntoRows(boxes: Box[]): Box[][] {
  const rows: Box[][] = [];
  for (const box of [...boxes].sort((one, other) => one.centerY - other.centerY)) {
    const row = rows.at(-1);
    if (row !== undefined) {
      const rowCenter = float(average(row.map((member) => member.centerY)));
      const rowHeight = float(average(row.map((member) => member.height)));
      const tolerance = float(ROW_TOLERANCE * Math.max(Math.min(box.height, rowHeight), 1));
      if (Math.abs(float(box.centerY - rowCenter)) <= tolerance) {
        row.push(box);
        continue;
      }
    }
    rows.push([box]);
  }
  return rows;
}

function average(values: number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function scale(offset: number, extent: number): number {
  return Math.min(Math.max(Math.round(float(float(offset / extent) * SCALE)), 0), SCALE);
}

/** One fragment in quotes, with the angle brackets of the bill swapped for look-alikes, so that no text closes or opens a tag of the message. */
function quote(text: string): string {
  const cleaned = text.replace(/[\n\r]/g, " ").replace(/</g, "‹").replace(/>/g, "›");
  return `"${cleaned.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}
