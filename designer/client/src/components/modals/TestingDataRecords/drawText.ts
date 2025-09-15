import type { FullTheme } from "@glideapps/glide-data-grid/src/common/styles";
import type { Theme } from "@mui/material";

export const LINE_HEIGHT = 14;
export const paddingX = 8;
export const paddingY = 2;
export const SPLIT_SEPARATOR = " ";
export const DEFAULT_ROW_HEADER = 32;
const FONT_FAMILY = "Roboto Mono, Monaco, monospace";
const FONT_BASE = `14px ${FONT_FAMILY}`;
const FONT_MEASURE = `13px ${FONT_FAMILY}`;
const AVG_CHAR_WIDTH = 7;

let measureCtx: CanvasRenderingContext2D | null = null;
function ensureMeasureCtx(): CanvasRenderingContext2D | null {
    if (measureCtx) return measureCtx;
    const canvas = typeof document !== "undefined" ? document.createElement("canvas") : null;
    const ctx = canvas ? canvas.getContext("2d") : null;
    if (ctx) {
        ctx.font = FONT_MEASURE;
        measureCtx = ctx;
    }
    return measureCtx;
}

// --- Key/value token parsing ---
const ENTRY_REGEX = /^(.*?)\s\*\*(.*?)\*\*$/; // key<space>**value** (non-greedy key)
function parseEntryToken(token: string): { key: string; value: string } | null {
    const m = ENTRY_REGEX.exec(token);
    if (!m) return null;
    const key = m[1] ?? "";
    const value = m[2] ?? "";
    if (value === "") return null;
    return { key, value };
}

// Helper: find longest prefix of value fitting in given width (binary search for efficiency)
function fitValuePrefix(value: string, maxWidth: number, ctx: CanvasRenderingContext2D): string {
    if (ctx.measureText(value).width <= maxWidth) return value;
    let lo = 0;
    let hi = value.length;
    while (lo < hi) {
        const mid = Math.ceil((lo + hi) / 2);
        const slice = value.slice(0, mid);
        if (ctx.measureText(slice).width <= maxWidth) {
            lo = mid;
        } else {
            hi = mid - 1;
        }
    }
    return value.slice(0, lo);
}

export function drawFieldForDisplay(
    ctx: CanvasRenderingContext2D,
    text: string,
    rect: { x: number; y: number; width: number; height: number },
    theme: FullTheme,
    muiTheme: Theme,
): void {
    const { x, y, width, height } = rect;
    const lineHeight = LINE_HEIGHT * theme.lineHeight;
    const maxContentRight = x + width - paddingX;

    const tokens = formatDataRecordsEntries(text);
    if (!tokens.length) return;

    let currentX = x + paddingX;
    let currentY = y + paddingY + lineHeight; // baseline of first line
    const measure = (s: string) => ctx.measureText(s).width;

    const advanceLine = () => {
        currentY += lineHeight;
        currentX = x + paddingX;
    };

    for (let ti = 0; ti < tokens.length; ti++) {
        if (currentY > y + height - paddingY) break; // no more vertical space
        const raw = tokens[ti];
        const parsed = parseEntryToken(raw);
        if (!parsed) {
            // Fallback plain text token wrapping
            let remaining = raw;
            ctx.font = FONT_BASE;
            ctx.fillStyle = theme.textDark;
            while (remaining.length) {
                const available = maxContentRight - currentX;
                if (available <= 0) {
                    advanceLine();
                    if (currentY > y + height - paddingY) break;
                    continue;
                }
                const slice = fitValuePrefix(remaining, available, ctx);
                if (!slice) {
                    // force wrap single char
                    advanceLine();
                    if (currentY > y + height - paddingY) break;
                    continue;
                }
                ctx.fillText(slice, currentX, currentY);
                currentX += measure(slice);
                remaining = remaining.slice(slice.length);
                if (remaining.length) {
                    advanceLine();
                }
            }
        } else {
            const { key, value } = parsed;
            ctx.font = FONT_BASE;
            // Draw key if any with wrapping if not enough room
            if (key) {
                const keyWidth = measure(key);
                const spaceWidth = measure(" ");
                if (currentX !== x + paddingX && currentX + keyWidth + spaceWidth > maxContentRight) {
                    advanceLine();
                    if (currentY > y + height - paddingY) break;
                }
                ctx.fillStyle = muiTheme.palette.custom.codeEditor.objectKeys.color;
                ctx.fillText(key, currentX, currentY);
                currentX += keyWidth;
                // space after key
                if (currentX + spaceWidth > maxContentRight) {
                    advanceLine();
                    if (currentY > y + height - paddingY) break;
                } else {
                    ctx.fillText(" ", currentX, currentY);
                    currentX += spaceWidth;
                }
            }
            // Draw value with intra-token wrapping
            ctx.fillStyle = fillSpecialTextStyle(value, muiTheme);
            let remainingVal = value;
            while (remainingVal.length) {
                const available = maxContentRight - currentX;
                if (available <= 0) {
                    advanceLine();
                    if (currentY > y + height - paddingY) break;
                    continue;
                }
                const part = fitValuePrefix(remainingVal, available, ctx);
                if (!part) {
                    advanceLine();
                    if (currentY > y + height - paddingY) break;
                    continue;
                }
                ctx.fillText(part, currentX, currentY);
                currentX += measure(part);
                remainingVal = remainingVal.slice(part.length);
                if (remainingVal.length) {
                    advanceLine();
                    if (currentY > y + height - paddingY) break;
                }
            }
        }
        // Space between tokens (only if not last)
        if (ti < tokens.length - 1) {
            const spaceWidth = measure(" ");
            if (currentX + spaceWidth > maxContentRight) {
                advanceLine();
                if (currentY > y + height - paddingY) break;
            } else {
                ctx.fillText(" ", currentX, currentY);
                currentX += spaceWidth;
            }
        }
    }
}

const fillSpecialTextStyle = (val: string, muiTheme: Theme) => {
    const isBoolean = val === "true" || val === "false";
    const isNumeric = val !== "" && !isNaN(Number(val));
    return isBoolean || isNumeric ? muiTheme.palette.custom.codeEditor.numeric.color : muiTheme.palette.custom.codeEditor.string.color;
};

// Reusable traversal producing entry tokens "key **value**"
interface TraverseOptions {
    maxDepth: number;
    maxEntries: number;
}
const traverseAndCollect = (
    value: unknown,
    path: string,
    entries: string[],
    seen: WeakSet<object>,
    opts: TraverseOptions,
    produced: { n: number },
    depth: number,
) => {
    if (produced.n >= opts.maxEntries) return;
    if (depth > opts.maxDepth) {
        const key = path || "$";
        if (produced.n < opts.maxEntries) {
            entries.push(`${key} **…**`);
            produced.n++;
        }
        return;
    }
    const push = (k: string, v: string) => {
        if (produced.n >= opts.maxEntries) return;
        entries.push(`${k} **${v}` + `**`);
        produced.n++;
    };
    const isPrim = (v: unknown): v is string | number | boolean | null =>
        v === null || ["string", "number", "boolean"].includes(typeof v as string);
    if (isPrim(value)) {
        const safe = value === null ? "null" : String(value).replace(/ /g, "\u00A0");
        push(path || "$", safe);
        return;
    }
    if (Array.isArray(value)) {
        if (value.length === 0) {
            push(path || "$", "[]");
            return;
        }
        value.forEach((item, i) => traverseAndCollect(item, path ? `${path}[${i}]` : `$[${i}]`, entries, seen, opts, produced, depth + 1));
        return;
    }
    if (value && typeof value === "object") {
        if (seen.has(value)) {
            push(path || "$", "[circular]");
            return;
        }
        seen.add(value as object);
        const keys = Object.keys(value as object);
        if (keys.length === 0) {
            push(path || "$", "{}");
            return;
        }
        keys.forEach((k) =>
            traverseAndCollect((value as Record<string, unknown>)[k], path ? `${path}.${k}` : k, entries, seen, opts, produced, depth + 1),
        );
        return;
    }
    push(path || "$", String(value));
};

export const formatDataRecordsEntries = (raw?: string): string[] => {
    if (!raw) return [];
    try {
        const parsed = JSON.parse(raw);
        const entries: string[] = [];
        traverseAndCollect(parsed, "", entries, new WeakSet<object>(), { maxDepth: 8, maxEntries: 2000 }, { n: 0 }, 0);
        return entries;
    } catch {
        return [raw];
    }
};

export const formatDataRecordsVariablesForDisplay = (raw?: string): string => {
    return formatDataRecordsEntries(raw).join(" ");
};

// Legacy export (still used elsewhere) — unchanged logic
export const getRowLines = (words: string[], maxTextWidth: number): string[] => {
    const ctx = ensureMeasureCtx();
    const measure = (t: string) => (ctx ? ctx.measureText(t).width : t.length * AVG_CHAR_WIDTH);
    let line = "";
    const lines: string[] = [];
    for (let n = 0; n < words.length; n++) {
        const testLine = line + words[n] + SPLIT_SEPARATOR;
        const testWidth = measure(testLine);
        if (testWidth > maxTextWidth && n > 0) {
            lines.push(line);
            line = words[n] + SPLIT_SEPARATOR;
        } else {
            line = testLine;
        }
    }
    lines.push(line);
    return lines;
};
