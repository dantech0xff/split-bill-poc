import { correction, generate } from "lean-qr";
import { useEffect, useRef, useState } from "preact/hooks";

const DARK = [0, 0, 0, 255] as const;
const LIGHT = [255, 255, 255, 255] as const;

/**
 * [payload] as a QR code, drawn pixel by pixel on a canvas: one pixel per module, black on white
 * with the quiet zone included in any theme, scaled up by CSS without smoothing.
 */
export function QrCode({ payload, label }: { payload: string; label: string }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const element = canvas.current;
    if (element === null) return;
    try {
      generate(payload, { minCorrectionLevel: correction.M }).toCanvas(element, { on: DARK, off: LIGHT, pad: 4 });
      setFailed(false);
    } catch {
      setFailed(true);
    }
  }, [payload]);

  if (failed) {
    return <p class="type-body-medium supporting">The QR code could not be drawn here. Use the details below.</p>;
  }
  return <canvas ref={canvas} class="qr" role="img" aria-label={label} width={1} height={1} />;
}
