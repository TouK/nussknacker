import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from "@mui/material";
import React, { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";

import type { TestingDataRecords } from "./Table";

interface Props {
    sourceId: string;
    onRowsAdded: (rows: TestingDataRecords[]) => void;
    defaultVariables?: string;
    disabled?: boolean;
}

function isNkFormat(obj: unknown): obj is { input: unknown; inputMeta: unknown } {
    return typeof obj === "object" && obj !== null && "input" in obj && "inputMeta" in obj;
}

function toRecord(parsed: unknown, defaultTemplate: Record<string, unknown> | null): unknown {
    return isNkFormat(parsed) ? parsed : { ...defaultTemplate, input: parsed };
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

    // Try whole text as JSON first (handles pretty-printed single object or array)
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

    // NDJSON: one JSON object per line
    const lines = trimmed
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean);
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

export const PasteRecordsButton = ({ sourceId, onRowsAdded, defaultVariables, disabled }: Props) => {
    const { t } = useTranslation();
    const [open, setOpen] = useState(false);
    const [text, setText] = useState("");
    const [parseError, setParseError] = useState<string | null>(null);

    const handleOpen = useCallback(() => {
        setText("");
        setParseError(null);
        setOpen(true);
    }, []);

    const handleClose = useCallback(() => setOpen(false), []);

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
            setOpen(false);
        }
    }, [text, sourceId, defaultVariables, onRowsAdded, t]);

    const recordCount = React.useMemo(() => {
        const trimmed = text.trim();
        if (!trimmed) return 0;
        try {
            const parsed = JSON.parse(trimmed);
            return Array.isArray(parsed) ? parsed.length : 1;
        } catch {
            return trimmed
                .split("\n")
                .map((l) => l.trim())
                .filter(Boolean).length;
        }
    }, [text]);

    return (
        <>
            <Button
                size="small"
                variant="text"
                onClick={handleOpen}
                disabled={disabled}
                sx={{ fontSize: "14px", mr: 2, textTransform: "none" }}
            >
                {t("pasteRecords.button", "Paste JSON lines")}
            </Button>

            <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
                <DialogTitle>{t("pasteRecords.title", "Paste JSON records")}</DialogTitle>
                <DialogContent>
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
                            {recordCount > 0 ? t("pasteRecords.recordCount", "{{count}} line(s) detected", { count: recordCount }) : ""}
                        </Typography>
                        {parseError && (
                            <Typography variant="caption" color="warning.main">
                                {parseError}
                            </Typography>
                        )}
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleClose}>{t("dialog.button.cancel", "Cancel")}</Button>
                    <Button onClick={handleConfirm} variant="contained" disabled={!text.trim()}>
                        {t("pasteRecords.confirm", "Add {{count}} record(s)", { count: recordCount })}
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};
