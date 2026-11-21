// @vitest-environment node
// The stylesheets are read from disk, which a page cannot do.
import { describe, expect, it } from "vitest";
import { sourceText } from "./source-text.mjs";

const appCss = sourceText("src/app.css");
const tokensCss = sourceText("src/tokens.css");

const FONT_FACE = /@font-face\s*\{[^}]*\}/g;

/** The weights [css] loads a face of, by the name of the family, from its `@font-face` rules. */
function faces(css: string): Map<string, number[]> {
  const loaded = new Map<string, number[]>();
  for (const [rule] of css.matchAll(FONT_FACE)) {
    const family = /font-family:\s*"([^"]+)"/.exec(rule)?.[1];
    const weight = Number(/font-weight:\s*(\d+)/.exec(rule)?.[1]);
    if (family === undefined || Number.isNaN(weight)) throw new Error(`An @font-face without a family or a weight: ${rule}`);
    loaded.set(family, [...(loaded.get(family) ?? []), weight]);
  }
  return loaded;
}

/** The custom properties [css] sets for the light theme, by name without their dashes. */
function lightTokens(css: string): Map<string, string> {
  const root = css.slice(0, css.indexOf("@media"));
  return new Map([...root.matchAll(/--([a-z0-9-]+):\s*([^;]+);/g)].map((match) => [match[1] ?? "", (match[2] ?? "").trim()]));
}

describe("the weights of the pages' type", () => {
  it("are those of the three faces the stylesheet loads", () => {
    expect(faces(appCss)).toEqual(
      new Map([
        ["Bricolage Grotesque", [800]],
        ["Be Vietnam Pro", [400, 600]],
      ]),
    );
  });

  it("ask, style by style, for a weight of its family that has a face, so that no browser picks another or makes one up", () => {
    const tokens = lightTokens(tokensCss);
    const loaded = faces(appCss);
    const styles = [...tokens.keys()].filter((name) => /^sv-type-[a-z-]+-weight$/.test(name)).map((name) => name.slice("sv-type-".length, -"-weight".length));

    expect(styles.length).toBeGreaterThan(10);
    for (const style of styles) {
      const family = /^var\(--([a-z-]+)\)$/.exec(tokens.get(`sv-type-${style}-family`) ?? "")?.[1];
      const name = /^"([^"]+)"/.exec(tokens.get(family ?? "") ?? "")?.[1] ?? "";
      expect(loaded.get(name) ?? [], `${style}: ${name}`).toContain(Number(tokens.get(`sv-type-${style}-weight`)));
    }
  });

  it("are set in the stylesheet only through the styles' tokens", () => {
    const rules = appCss.replace(/\/\*[\s\S]*?\*\//g, "").replace(FONT_FACE, "");

    for (const [, value] of rules.matchAll(/font-weight:\s*([^;]+);/g)) expect(value).toMatch(/^var\(--sv-type-[a-z-]+-weight\)$/);
    // A control takes the type of the text around it as it is.
    for (const [, value] of rules.matchAll(/\bfont:\s*([^;]+);/g)) expect(value).toMatch(/^(?:inherit$|var\(--sv-type-[a-z-]+-weight\) )/);
    expect(rules).not.toMatch(/\b(?:bold|bolder|lighter)\b/);
  });
});
