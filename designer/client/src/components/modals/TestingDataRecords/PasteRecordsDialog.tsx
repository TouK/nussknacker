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
import { parseRecords } from "./parseRecords";
import type { TestingDataRecords } from "./types";

export interface PasteRecordsDialogData {
    sourceId: string;
    onRowsAdded: (rows: TestingDataRecords[]) => void;
    defaultVariables?: string;
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
            return trimmed.split("\n").filter((l) => l.trim()).length;
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
