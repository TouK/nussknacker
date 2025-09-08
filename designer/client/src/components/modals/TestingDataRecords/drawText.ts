import type { FullTheme } from "@glideapps/glide-data-grid/src/common/styles";

export const LINE_HEIGHT = 14;
export const paddingX = 8;
export const paddingY = 2;
export const SPLIT_SEPARATOR = " ";
export const DEFAULT_ROW_HEADER = 32;

const FONT_BASE = "14px Inter";
const FONT_BOLD = "bolder 14px Inter";
const FONT_MEASURE = "13px Inter";
const BOLD_SEGMENT_REGEX = /\*\*(.*?)\*\*/g;
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

export function drawTextWithBoldSegments(
    ctx: CanvasRenderingContext2D,
    text: string,
    rect: { x: number; y: number; width: number; height: number },
    theme: FullTheme,
): void {
    const { x, y, width, height } = rect;
    const lineHeight = LINE_HEIGHT * theme.lineHeight;
    const maxTextWidth = width - paddingX * 2;

    const formattedText = formatDataRecordsVariablesForDisplay(text);
    if (!formattedText) return;

    ctx.fillStyle = theme.textDark;

    const words = formattedText.split(SPLIT_SEPARATOR);
    const linesOfText = getRowLines(words, maxTextWidth);

    let currentYPosition = y + paddingY + lineHeight;
    for (const line of linesOfText) {
        if (currentYPosition > y + height - paddingY) break; // prevent text overflow

        const boldTextSegments = [...line.matchAll(BOLD_SEGMENT_REGEX)];
        let currentXPosition = x + paddingX;
        let lastProcessedIndex = 0;

        for (const boldSegment of boldTextSegments) {
            const [fullMatch, boldText] = boldSegment;
            const startIndex = boldSegment.index || 0;

            // Draw normal text before the bold segment
            const normalText = line.slice(lastProcessedIndex, startIndex);
            if (normalText) {
                ctx.font = FONT_BASE;
                ctx.fillStyle = theme.textDark;
                ctx.fillText(normalText, currentXPosition, currentYPosition);
                currentXPosition += ctx.measureText(normalText).width;
            }
            ctx.font = FONT_BOLD;
            ctx.fillText(boldText, currentXPosition, currentYPosition);
            currentXPosition += ctx.measureText(boldText).width;
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

export const formatDataRecordsVariablesForDisplay = (raw?: string): string => {
    if (!raw) return "";
    try {
        const parsed = JSON.parse(raw);
        if (typeof parsed !== "object" || parsed === null) {
            return String(parsed);
        }
        const entries: string[] = [];
        const traverse = (value: unknown, path: string) => {
            if (typeof value === "string" || typeof value === "number" || typeof value === "boolean" || value === null) {
                const key = path || "$";
                entries.push(`**${key}** ${value}`);
                return;
            }
            if (Array.isArray(value)) {
                value.forEach((item, index) => traverse(item, `${path}[${index}]`));
                return;
            }
            if (typeof value === "object") {
                Object.entries(value).forEach(([key, val]) => traverse(val, path ? `${path}.${key}` : key));
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
