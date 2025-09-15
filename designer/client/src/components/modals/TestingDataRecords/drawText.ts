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
const SPECIAL_SEGMENT_REGEX = /\*\*(.*?)\*\*/g;
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

function measureLineWidth(text: string): number {
    const ctx = ensureMeasureCtx();
    if (!ctx) return text.length * AVG_CHAR_WIDTH;
    return ctx.measureText(text).width;
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
    const maxTextWidth = width - paddingX * 2;

    const formattedText = formatDataRecordsVariablesForDisplay(text);
    if (!formattedText) return;

    const words = formattedText.split(SPLIT_SEPARATOR);
    const linesOfText = getRowLines(words, maxTextWidth);

    let currentYPosition = y + paddingY + lineHeight;
    for (const line of linesOfText) {
        if (currentYPosition > y + height - paddingY) break; // prevent text overflow

        const specialTextSegments = [...line.matchAll(SPECIAL_SEGMENT_REGEX)];
        let currentXPosition = x + paddingX;
        let lastProcessedIndex = 0;

        for (const specialSegment of specialTextSegments) {
            const [fullMatch, specialText] = specialSegment;
            // preserve 0 (match at start) — only fallback when index is null/undefined
            const startIndex = specialSegment.index ?? 0;

            // Draw normal text before the special segment (if any)
            const normalText = line.slice(lastProcessedIndex, startIndex);
            if (normalText) {
                ctx.font = FONT_BASE;
                ctx.fillStyle = muiTheme.palette.custom.codeEditor.objectKeys.color;
                ctx.fillText(normalText, currentXPosition, currentYPosition);
                currentXPosition += ctx.measureText(normalText).width;
            }

            // Draw the special/value segment
            ctx.font = FONT_BASE;
            ctx.fillStyle = fillSpecialTextStyle(specialText, muiTheme);
            ctx.fillText(specialText, currentXPosition, currentYPosition);
            currentXPosition += ctx.measureText(specialText).width;

            lastProcessedIndex = startIndex + fullMatch.length;
        }

        const remainingText = line.slice(lastProcessedIndex);
        if (remainingText) {
            ctx.font = theme.baseFontFull;
            ctx.fillStyle = theme.textDark;
            ctx.fillText(remainingText, currentXPosition, currentYPosition);
        }
        currentYPosition += lineHeight;
    }
}

const fillSpecialTextStyle = (val: string, muiTheme: Theme) => {
    const isBoolean = val === "true" || val === "false";
    const isNumeric = val !== "" && !isNaN(Number(val));

    if (isBoolean || isNumeric) {
        return muiTheme.palette.custom.codeEditor.numeric.color;
    } else {
        return muiTheme.palette.custom.codeEditor.string.color;
    }
};

export const formatDataRecordsVariablesForDisplay = (raw?: string): string => {
    if (!raw) return "";
    try {
        const parsed = JSON.parse(raw);
        if (typeof parsed !== "object" || parsed === null) {
            return String(parsed);
        }
        const entries: string[] = [];

        const safeValue = (v: unknown) => {
            if (v === null) return "null";
            // preserve visual spaces but prevent splitting by replacing with NBSP
            return String(v).replace(/ /g, "\u00A0");
        };

        const traverse = (value: unknown, path: string) => {
            if (typeof value === "string" || typeof value === "number" || typeof value === "boolean" || value === null) {
                const key = path || "$";
                entries.push(`${key} **${safeValue(value)}**`);
                return;
            }
            if (Array.isArray(value)) {
                value.forEach((item, index) => traverse(item, `${path}[${index}]`));
                return;
            }
            if (typeof value === "object") {
                Object.entries(value).forEach(([k, v]) => traverse(v, path ? `${path}.${k}` : k));
                return;
            }
        };

        traverse(parsed, "");
        return entries.join(" ");
    } catch {
        return raw;
    }
};

export const getRowLines = (words: string[], maxTextWidth: number): string[] => {
    let line = "";
    const lines: string[] = [];
    for (let n = 0; n < words.length; n++) {
        const testLine = line + words[n] + SPLIT_SEPARATOR;
        const testWidth = measureLineWidth(testLine);
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
