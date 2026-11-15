import { type VNode, render } from "preact";
import { act } from "preact/test-utils";
import { afterEach, vi } from "vitest";

const mounted: HTMLElement[] = [];

/** Renders [vnode] into a fresh element of the document, with effects run. */
export function mount(vnode: VNode): HTMLElement {
  const container = document.createElement("div");
  document.body.appendChild(container);
  act(() => {
    render(vnode, container);
  });
  mounted.push(container);
  return container;
}

afterEach(() => {
  for (const container of mounted.splice(0)) {
    act(() => {
      render(null, container);
    });
    container.remove();
  }
});

/** The text of [element], its pieces apart and runs of white space made single, as a person reads it. */
export function textOf(element: Element | null): string {
  if (element === null) return "";
  const parts: string[] = [];
  const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
  for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) parts.push(node.textContent ?? "");
  return parts.join(" ").replace(/\s+/g, " ").trim();
}

export function all(root: ParentNode, selector: string): Element[] {
  return Array.from(root.querySelectorAll(selector));
}

/**
 * Gives canvases a 2D context that records what is drawn, as happy-dom has none; returns the
 * spy of putImageData.
 */
export function stubCanvas(): ReturnType<typeof vi.fn> {
  const putImageData = vi.fn();
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(
    () =>
      ({
        createImageData: (width: number, height: number) => ({ width, height, data: new Uint8ClampedArray(width * height * 4) }),
        putImageData,
      }) as unknown as CanvasRenderingContext2D,
  );
  return putImageData;
}
