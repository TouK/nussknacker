import { Box, TextField, Typography } from "@mui/material";
import type { WindowButtonProps, WindowContentProps } from "@touk/window-manager";
import React, { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { getMaxTestingRecords } from "../../../reducers/selectors/settings";
import { getTestData } from "../../../reducers/selectors/testCases";
import { useAppSelector } from "../../../store/storeHelpers";
import { LoadingButtonTypes } from "../../../windowManager/LoadingButton";
import { WindowContent } from "../../../windowManager/WindowContent";
import type { WindowKind } from "../../../windowManager/WindowKind";
import type { TestingDataRecords } from "./Table";

export interface PasteRecordsDialogData {
    sourceId: string;
    onRowsAdded: (rows: TestingDataRecords[]) => void;
    defaultVariables?: string;
}

function isNkFormat(obj: unknown): obj is { input: unknown; inputMeta: unknown } {
    return typeof obj === "object" && obj !== null && "input" in obj && "inputMeta" in obj;
}

function toRecord(parsed: unknown, defaultTemplate: Record<string, unknown> | null): unknown {
    return isNkFormat(parsed) ? parsed : { ...defaultTemplate, input: parsed };
}

function splitTopLevelJsonObjects(text: string): string[] {
    const chunks: string[] = [];
    let depth = 0;
    let start = 0;
    let inString = false;
    let escape = false;

    for (let i = 0; i < text.length; i++) {
        const ch = text[i];
        if (escape) {
            escape = false;
            continue;
        }
        if (ch === "\\" && inString) {
            escape = true;
            continue;
        }
        if (ch === '"') {
            inString = !inString;
            continue;
        }
        if (inString) continue;

        if (ch === "{" || ch === "[") {
            if (depth === 0) start = i;
            depth++;
        } else if (ch === "}" || ch === "]") {
            depth--;
            if (depth === 0) {
                chunks.push(text.slice(start, i + 1));
            }
        }
    }
    return chunks;
}

function splitNdjsonLines(text: string): string[] {
    return text
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean);
}

function parseRecords(text: string, sourceId: string, defaultVariables?: string): { rows: TestingDataRecords[]; errorCount: number } {
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

    // Try splitting into multiple top-level JSON objects (handles pretty-printed multi-line objects)
    const topLevelChunks = splitTopLevelJsonObjects(trimmed);
    if (topLevelChunks.length > 1) {
        let errorCount = 0;
        const rows: TestingDataRecords[] = [];
        for (const chunk of topLevelChunks) {
            try {
                const parsed = JSON.parse(chunk);
                rows.push({ sourceId, variables: JSON.stringify(toRecord(parsed, defaultTemplate), null, 2) });
            } catch {
                errorCount++;
            }
        }
        return { rows, errorCount };
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

export const PasteRecordsDialog = (props: WindowContentProps<WindowKind, PasteRecordsDialogData>) => {
    const { t } = useTranslation();
    const { sourceId, onRowsAdded, defaultVariables } = props.data.meta;

    const maxTestingRecords = useAppSelector(getMaxTestingRecords);
    const currentRecordsCount = useAppSelector(getTestData).length;

    const [text, setText] = useState("");
    const [parseError, setParseError] = useState<string | null>(null);

    const recordCount = useMemo(() => {
        const trimmed = text.trim();
        if (!trimmed) return 0;
        try {
            const parsed = JSON.parse(trimmed);
            return Array.isArray(parsed) ? parsed.length : 1;
        } catch {
            const topLevel = splitTopLevelJsonObjects(trimmed);
            if (topLevel.length > 1) return topLevel.length;
            return splitNdjsonLines(trimmed).length;
        }
    }, [text]);

    const handleChange = useCallback((e: React.ChangeEvent<HTMLTextAreaElement>) => {
        setText(e.target.value);
        setParseError(null);
    }, []);

    const handleConfirm = useCallback(() => {
        const { rows, errorCount } = parseRecords(text, sourceId, defaultVariables);

        if (rows.length === 0) {
            setParseError(t("pasteRecords.noValidLines", "No valid JSON lines found."));
            return;
        }

        if (errorCount > 0) {
            setParseError(t("pasteRecords.someInvalid", "{{errorCount}} line(s) could not be parsed and were skipped.", { errorCount }));
        }

        onRowsAdded(rows);
        if (errorCount === 0) {
            props.close();
        }
    }, [text, sourceId, defaultVariables, onRowsAdded, props, t]);

    const limitExceeded = currentRecordsCount + recordCount > maxTestingRecords;

    const buttons = useMemo<WindowButtonProps[]>(
        () => [
            {
                title: t("dialog.button.cancel", "Cancel"),
                action: () => props.close(),
                classname: LoadingButtonTypes.secondaryButton,
            },
            {
                title: t("pasteRecords.confirm", {
                    count: recordCount,
                    defaultValue_one: "Add {{count}} record",
                    defaultValue_other: "Add {{count}} records",
                }),
                action: handleConfirm,
                disabled: !text.trim() || limitExceeded,
            },
        ],
        [t, props, recordCount, handleConfirm, text, limitExceeded],
    );

    return (
        <WindowContent {...props} buttons={buttons}>
            <Box sx={{ p: 2 }}>
                <Typography variant="body2" color="text.secondary" mb={1}>
                    {t(
                        "pasteRecords.hint",
                        "Accepts: a single JSON object (pretty-printed or compact), a JSON array, or one JSON object per line (NDJSON). Raw events without input/inputMeta will be wrapped automatically.",
                    )}
                </Typography>
                <TextField
                    multiline
                    fullWidth
                    minRows={8}
                    maxRows={20}
                    value={text}
                    onChange={handleChange}
                    placeholder={'{"input": {...}, "inputMeta": {...}}\n{"input": {...}, "inputMeta": {...}}'}
                    inputProps={{ style: { fontFamily: "monospace", fontSize: 12 } }}
                    autoFocus
                />
                <Box mt={1} display="flex" justifyContent="space-between" alignItems="center">
                    <Typography variant="caption" color="text.secondary">
                        {recordCount > 0
                            ? t("pasteRecords.recordCount", {
                                  count: recordCount,
                                  defaultValue_one: "{{count}} line detected",
                                  defaultValue_other: "{{count}} lines detected",
                              })
                            : ""}
                    </Typography>
                    {limitExceeded ? (
                        <Typography variant="caption" color="error.main">
                            {t("pasteRecords.limitExceeded", "Limit of {{max}} records would be exceeded.", { max: maxTestingRecords })}
                        </Typography>
                    ) : (
                        parseError && (
                            <Typography variant="caption" color="warning.main">
                                {parseError}
                            </Typography>
                        )
                    )}
                </Box>
            </Box>
        </WindowContent>
    );
};

export default PasteRecordsDialog;
