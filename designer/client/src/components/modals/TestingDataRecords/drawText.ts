import type { FullTheme } from "@glideapps/glide-data-grid/src/common/styles";
import type { Theme } from "@mui/material";

interface Token {
    key: string;
    value: string;
    valueKind: "numeric" | "boolean" | "string";
}
interface Fragment {
    text: string;
    x: number;
    y: number;
    color: string;
}
interface TraverseOptions {
    maxDepth: number;
    maxEntries: number;
}

export const LINE_HEIGHT = 14;
export const paddingX = 8;
export const paddingY = 2;
export const SPLIT_SEPARATOR = " ";
export const DEFAULT_ROW_HEADER_HEIGHT = 32;
export const DEFAULT_TRAILING_ROW_HEIGHT = 32;
const FONT_FAMILY = "Roboto Mono, Monaco, monospace";
const FONT_BASE = `14px ${FONT_FAMILY}`;
const FONT_MEASURE = `13px ${FONT_FAMILY}`;
const AVG_CHAR_WIDTH = 7;
const MAX_TRAVERSE_DEPTH = 8;
const MAX_TOKENS = 2000;

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

function classifyValue(value: string): Token["valueKind"] {
    if (value === "true" || value === "false") return "boolean";
    if (value !== "" && !isNaN(Number(value))) return "numeric";
    return "string";
}

function tokenizeDataRecords(raw?: string): Token[] {
    if (!raw) return [];
    let parsed: unknown;
    try {
        parsed = JSON.parse(raw);
    } catch {
        return [];
    }
    const tokens: Token[] = [];
    const opts: TraverseOptions = { maxDepth: MAX_TRAVERSE_DEPTH, maxEntries: MAX_TOKENS };
    const seen = new WeakSet<object>();
    let produced = 0;
    const push = (key: string, value: string): void => {
        if (produced >= opts.maxEntries) return;
        tokens.push({ key, value, valueKind: classifyValue(value) });
        produced++;
    };
    const isPrim = (v: unknown): v is string | number | boolean | null =>
        v === null || ["string", "number", "boolean"].includes(typeof v as string);
    const walk = (val: unknown, path: string, depth: number): void => {
        if (depth > opts.maxDepth) {
            push(path || "$", "…");
            return;
        }
        if (isPrim(val)) {
            push(path || "$", val === null ? "null" : String(val));
            return;
        }
        if (Array.isArray(val)) {
            if (val.length === 0) {
                push(path || "$", "[]");
                return;
            }
            val.forEach((item, i) => walk(item, path ? `${path}[${i}]` : `$[${i}]`, depth + 1));
            return;
        }
        if (val && typeof val === "object") {
            if (seen.has(val as object)) {
                push(path || "$", "[circular]");
                return;
            }
            seen.add(val as object);
            const keys = Object.keys(val as object);
            if (keys.length === 0) {
                push(path || "$", "{}");
                return;
            }
            keys.forEach((k) => walk((val as Record<string, unknown>)[k], path ? `${path}.${k}` : k, depth + 1));
            return;
        }
        push(path || "$", String(val));
    };
    walk(parsed, "", 0);
    return tokens;
}

function measureTextWidth(text: string, ctx: CanvasRenderingContext2D): number {
    return ctx.measureText(text).width;
}

function computeLineHeight(ctx: CanvasRenderingContext2D, theme: FullTheme): number {
    const sample = ctx.measureText("Mg");
    const ascent = sample.actualBoundingBoxAscent ?? 0;
    const descent = sample.actualBoundingBoxDescent ?? 0;
    const measured = ascent + descent;
    const target = LINE_HEIGHT * theme.lineHeight;
    return Math.max(Math.ceil(target), Math.ceil(measured));
}

function buildFragmentsForTokens(
    tokens: Token[],
    ctx: CanvasRenderingContext2D,
    rect: { x: number; y: number; width: number; height: number },
    maxRight: number,
    bottom: number,
    keyColor: string,
    kindColor: (k: Token["valueKind"]) => string,
    lineHeight: number,
): Fragment[] {
    const fragments: Fragment[] = [];
    let lineIndex = 0;
    let x = rect.x + paddingX;
    const baseY = rect.y + paddingY + lineHeight;
    const currentY = () => baseY + lineIndex * lineHeight;
    const advanceLine = (): boolean => {
        lineIndex++;
        x = rect.x + paddingX;
        if (currentY() > bottom) return false;
        return true;
    };
    const wrapAndPushWords = (text: string, color: string): boolean => {
        const baseLeft = rect.x + paddingX;
        const fullLineWidth = maxRight - baseLeft;
        const parts = text.split(/(\s+)/).filter((p) => p.length > 0);
        // If we have an intentionally empty display string (e.g. ""), still push it directly
        if (parts.length === 0 && text.length > 0) {
            const w = measureTextWidth(text, ctx);
            if (x !== baseLeft && x + w > maxRight) {
                if (!advanceLine()) return false;
            }
            fragments.push({ text, x, y: currentY(), color });
            x += w;
            return true;
        }
        for (let i = 0; i < parts.length; i++) {
            const part = parts[i];
            const isSpace = /^\s+$/.test(part);
            if (isSpace) {
                if (x > baseLeft && i < parts.length - 1) {
                    const w = measureTextWidth(part, ctx);
                    if (x + w > maxRight) {
                        if (!advanceLine()) return false;
                    } else {
                        fragments.push({ text: part, x, y: currentY(), color });
                        x += w;
                    }
                }
                continue;
            }
            const wordWidth = measureTextWidth(part, ctx);
            if (wordWidth > fullLineWidth) {
                let remaining = part;
                while (remaining.length) {
                    const avail = maxRight - x;
                    if (avail <= 0) {
                        if (!advanceLine()) return false;
                        continue;
                    }
                    let lo = 1;
                    let hi = remaining.length;
                    while (lo <= hi) {
                        const mid = Math.floor((lo + hi) / 2);
                        const slice = remaining.slice(0, mid);
                        if (measureTextWidth(slice, ctx) <= avail) lo = mid + 1;
                        else hi = mid - 1;
                    }
                    const fitLen = hi;
                    if (fitLen <= 0) {
                        if (!advanceLine()) return false;
                        continue;
                    }
                    const slice = remaining.slice(0, fitLen);
                    fragments.push({ text: slice, x, y: currentY(), color });
                    x += measureTextWidth(slice, ctx);
                    remaining = remaining.slice(fitLen);
                    if (remaining.length) {
                        if (!advanceLine()) return false;
                    }
                }
                continue;
            }
            if (x !== baseLeft && x + wordWidth > maxRight) {
                if (!advanceLine()) return false;
            }
            fragments.push({ text: part, x, y: currentY(), color });
            x += wordWidth;
            if (currentY() > bottom) return false;
        }
        return true;
    };
    for (let index = 0; index < tokens.length; index++) {
        if (currentY() > bottom) break;
        const token = tokens[index];
        const lineStart = rect.x + paddingX;
        const spaceChar = SPLIT_SEPARATOR;
        const spaceWidth = measureTextWidth(spaceChar, ctx);
        const displayValue = token.value === "" ? '""' : token.value; // show empty strings explicitly as ""
        if (token.key) {
            const keyWidth = measureTextWidth(token.key, ctx);
            const firstValueWordRaw = displayValue.split(/\s+/)[0] || "";
            const firstValueWordWidth = firstValueWordRaw ? measureTextWidth(firstValueWordRaw, ctx) : 0;
            const requireSameLine = firstValueWordRaw.length > 0;
            const requiredWidth = keyWidth + spaceWidth + (requireSameLine ? firstValueWordWidth : 0);
            if (x !== lineStart && x + requiredWidth > maxRight) {
                if (!advanceLine()) break;
            }
            fragments.push({ text: token.key, x, y: currentY(), color: keyColor });
            x += keyWidth;
            if (x + spaceWidth > maxRight) {
                if (!advanceLine()) break;
            } else {
                fragments.push({ text: spaceChar, x, y: currentY(), color: keyColor });
                x += spaceWidth;
            }
        }
        if (!wrapAndPushWords(displayValue, kindColor(token.valueKind))) break;
        if (index < tokens.length - 1 && currentY() <= bottom) {
            if (x + spaceWidth > maxRight) {
                if (!advanceLine()) break;
            } else {
                fragments.push({ text: spaceChar, x, y: currentY(), color: keyColor });
                x += spaceWidth;
            }
        }
    }
    return fragments;
}

function layoutTokens(
    tokens: Token[],
    ctx: CanvasRenderingContext2D,
    rect: { x: number; y: number; width: number; height: number },
    muiTheme: Theme,
    lineHeight: number,
): Fragment[] {
    const maxRight = rect.x + rect.width - paddingX;
    const bottom = rect.y + rect.height - paddingY;
    const keyColor = muiTheme.palette.custom.codeEditor.objectKeys.color;
    const kindColor = (k: Token["valueKind"]): string =>
        k === "numeric" || k === "boolean"
            ? muiTheme.palette.custom.codeEditor.numeric.color
            : muiTheme.palette.custom.codeEditor.string.color;
    return buildFragmentsForTokens(tokens, ctx, rect, maxRight, bottom, keyColor, kindColor, lineHeight);
}

function renderFragments(ctx: CanvasRenderingContext2D, fragments: Fragment[]): void {
    ctx.font = FONT_BASE;
    for (const f of fragments) {
        ctx.fillStyle = f.color;
        ctx.fillText(f.text, f.x, f.y);
    }
}

export function drawFieldForDisplay(
    ctx: CanvasRenderingContext2D,
    text: string,
    rect: { x: number; y: number; width: number; height: number },
    theme: FullTheme,
    muiTheme: Theme,
): void {
    const tokens = tokenizeDataRecords(text);
    if (!tokens.length) return;
    ctx.font = FONT_BASE;
    const lineHeight = computeLineHeight(ctx, theme);
    const fragments = layoutTokens(tokens, ctx, rect, muiTheme, lineHeight);
    renderFragments(ctx, fragments);
}

export const formatDataRecordsEntries = (raw?: string): string[] => tokenizeDataRecords(raw).map((t) => `${t.key} **${t.value}**`);
export const formatDataRecordsVariablesForDisplay = (raw?: string): string => formatDataRecordsEntries(raw).join(" ");
export const getRowLines = (words: string[], maxTextWidth: number): string[] => {
    const ctx = ensureMeasureCtx();
    const measureFn = (t: string) => (ctx ? ctx.measureText(t).width : t.length * AVG_CHAR_WIDTH);
    let line = "";
    const lines: string[] = [];
    for (let n = 0; n < words.length; n++) {
        const testLine = line + words[n] + SPLIT_SEPARATOR;
        const testWidth = measureFn(testLine);
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
