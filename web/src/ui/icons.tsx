import type { ComponentChildren } from "preact";

/**
 * Line icons as the approved web designs draw them, and as the app draws its own: strokes of the
 * text's colour with round ends, on a grid of 24. Drawn as SVG elements, never as markup text,
 * and hidden from screen readers, as the text beside each says what it means.
 */
function Icon({ children, weight = 2.2 }: { children: ComponentChildren; weight?: number }) {
  return (
    <svg
      class="icon"
      viewBox="0 0 24 24"
      width="24"
      height="24"
      fill="none"
      stroke="currentColor"
      stroke-width={weight}
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      {children}
    </svg>
  );
}

/** The tick of an item this browser picked, and of a split that is settled. */
export const CheckIcon = () => (
  <Icon weight={3.2}>
    <path d="m5 12.5 4.5 4.5L19 7.5" />
  </Icon>
);

/** Picks that are closed while the split is settled. */
export const LockIcon = () => (
  <Icon weight={2.4}>
    <rect x="5" y="11" width="14" height="10" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </Icon>
);

/** A row that opens to show more; it turns when the row is open. */
export const ExpandIcon = () => (
  <Icon weight={2.4}>
    <path d="m6 9 6 6 6-6" />
  </Icon>
);

/** A link that opens another site in a new tab. */
export const OpenInNewIcon = () => (
  <Icon>
    <path d="M14 4h6v6M20 4l-9 9M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4" />
  </Icon>
);

export const CopyIcon = () => (
  <Icon>
    <rect x="8" y="8" width="12" height="12" rx="2" />
    <path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2" />
  </Icon>
);

/** A link that does not exist. */
export const LinkOffIcon = () => (
  <Icon>
    <path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1M4 4l16 16" />
  </Icon>
);

/** A link whose time is over. */
export const ClockIcon = () => (
  <Icon>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3 2" />
  </Icon>
);

/** Too many requests: a wait before the next. */
export const HourglassIcon = () => (
  <Icon>
    <path d="M7 3h10M7 21h10M8 3v2.5a4 4 0 0 0 1.6 3.2L12 10.5l2.4-1.8A4 4 0 0 0 16 5.5V3M8 21v-2.5a4 4 0 0 1 1.6-3.2l2.4-1.8 2.4 1.8a4 4 0 0 1 1.6 3.2V21" />
  </Icon>
);

/** No connection. */
export const OfflineIcon = () => (
  <Icon>
    <path d="M2 8.5a15 15 0 0 1 4.5-2.8M10 5.1A15 15 0 0 1 22 8.5M5.5 12a10 10 0 0 1 3.3-2M15 10a10 10 0 0 1 3.5 2M9 15.5a5 5 0 0 1 6 0M12 19h.01M3 3l18 18" />
  </Icon>
);

/** Something that changed since the page last showed it. */
export const RefreshIcon = () => (
  <Icon weight={2.4}>
    <path d="M20 11a8 8 0 0 0-14.6-4.5M4 4v4h4M4 13a8 8 0 0 0 14.6 4.5M20 20v-4h-4" />
  </Icon>
);

/** Something to know before going on. */
export const InfoIcon = () => (
  <Icon weight={2.4}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 11v5.5M12 7.5v.01" />
  </Icon>
);

/** Something that went wrong. */
export const AlertIcon = () => (
  <Icon weight={2.4}>
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7.5v5.5M12 16.5v.01" />
  </Icon>
);

/** The tile in front of a notice: the yellow of notices, and [children], an icon that says there is something to know unless another is given. */
export function NoticeIcon({ children = <InfoIcon /> }: { children?: ComponentChildren }) {
  return (
    <span class="notice__icon" aria-hidden="true">
      {children}
    </span>
  );
}
