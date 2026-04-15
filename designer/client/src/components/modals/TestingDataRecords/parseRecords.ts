import type { TestingDataRecords } from "./types";

function isNkFormat(obj: unknown): obj is { input: unknown; inputMeta?: unknown } {
    return typeof obj === "object" && obj !== null && "input" in obj;
}

function toRecord(parsed: unknown, defaultTemplate: Record<string, unknown> | null): unknown {
    if (!isNkFormat(parsed)) {
        return { ...defaultTemplate, input: parsed };
    }
    // Already has 'input', but fill in missing fields from template (e.g. inputMeta) as defaults
    return defaultTemplate ? { ...defaultTemplate, ...parsed } : parsed;
}

function splitNdjsonLines(text: string): string[] {
    return text
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean);
}

export function parseRecords(
    text: string,
    sourceId: string,
    defaultVariables?: string,
): { rows: TestingDataRecords[]; errorCount: number } {
    let defaultTemplate: Record<string, unknown> | null = null;
    if (defaultVariables) {
        try {
            const parsed = JSON.parse(defaultVariables);
            if (typeof parsed === "object" && parsed !== null) {
                defaultTemplate = parsed as Record<string, unknown>;
            }
        } catch {
            // ignore
        }
    }

    const trimmed = text.trim();

    try {
        const parsed = JSON.parse(trimmed);
        if (Array.isArray(parsed)) {
            return {
                rows: parsed.map((item) => ({ sourceId, variables: JSON.stringify(toRecord(item, defaultTemplate), null, 2) })),
                errorCount: 0,
            };
        }
        if (typeof parsed === "object" && parsed !== null) {
            return {
                rows: [{ sourceId, variables: JSON.stringify(toRecord(parsed, defaultTemplate), null, 2) }],
                errorCount: 0,
            };
        }
    } catch {
        // not a single JSON — fall through to NDJSON
    }

    // Fallback: NDJSON (one compact JSON object per line)
    const lines = splitNdjsonLines(trimmed);
    let errorCount = 0;
    const rows: TestingDataRecords[] = [];
    for (const line of lines) {
        try {
            const parsed = JSON.parse(line);
            rows.push({ sourceId, variables: JSON.stringify(toRecord(parsed, defaultTemplate), null, 2) });
        } catch {
            errorCount++;
        }
    }
    return { rows, errorCount };
}
