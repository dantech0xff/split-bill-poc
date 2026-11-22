/** A new, empty folder among the system's temporary files. */
export function temporaryFolder(): string;
/** The names of the files in the folder at [parts], joined. */
export function filesIn(...parts: string[]): string[];
/** The bytes of the file at [parts], joined. */
export function bytesOf(...parts: string[]): Uint8Array;
/** Deletes the folder [folder] and all it holds. */
export function removeFolder(folder: string): void;
