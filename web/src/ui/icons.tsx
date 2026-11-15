/** Material Symbols drawn as SVG elements, never as markup text. */

interface IconProps {
  path: string;
}

function Icon({ path }: IconProps) {
  return (
    <svg class="icon" viewBox="0 0 24 24" width="24" height="24" aria-hidden="true" focusable="false">
      <path d={path} fill="currentColor" />
    </svg>
  );
}

export const CheckIcon = () => <Icon path="M9.55 18 3.85 12.3l1.43-1.43 4.27 4.28 9.18-9.18 1.42 1.43Z" />;

export const ExpandIcon = () => <Icon path="M12 15.38 6 9.38l1.4-1.41 4.6 4.6 4.6-4.6L18 9.38Z" />;

export const OpenInNewIcon = () => (
  <Icon path="M5 21q-.82 0-1.41-.59T3 19V5q0-.82.59-1.41T5 3h7v2H5v14h14v-7h2v7q0 .82-.59 1.41T19 21Zm4.7-5.3-1.4-1.4L17.6 5H14V3h7v7h-2V6.4Z" />
);

export const CopyIcon = () => (
  <Icon path="M9 18q-.82 0-1.41-.59T7 16V4q0-.82.59-1.41T9 2h9q.82 0 1.41.59T20 4v12q0 .82-.59 1.41T18 18Zm0-2h9V4H9Zm-4 6q-.82 0-1.41-.59T3 20V6h2v14h11v2Z" />
);
