const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;
const dayFormat = new Intl.DateTimeFormat("en", { dateStyle: "medium", timeZone: "UTC" });

/** A bill's day, `YYYY-MM-DD`, as "Nov 14, 2026"; text that is not a real day is shown as it is. */
export function formatBillDate(date: string): string {
  const match = DAY.exec(date);
  if (match === null) return date;
  const [year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const time = Date.UTC(year, month - 1, day);
  const back = new Date(time);
  if (back.getUTCFullYear() !== year || back.getUTCMonth() !== month - 1 || back.getUTCDate() !== day) return date;
  return dayFormat.format(time);
}
