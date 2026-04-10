import ContentPasteIcon from "@mui/icons-material/ContentPaste";
import { Button } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";

import { useWindows } from "../../../windowManager/useWindows";
import { WindowKind } from "../../../windowManager/WindowKind";
import type { PasteRecordsDialogData } from "./PasteRecordsDialog";
import type { TestingDataRecords } from "./Table";

interface Props {
    sourceId: string;
    onRowsAdded: (rows: TestingDataRecords[]) => void;
    defaultVariables?: string;
    disabled?: boolean;
    variant?: "text" | "outlined";
}

export const PasteRecordsButton = ({ sourceId, onRowsAdded, defaultVariables, disabled, variant = "text" }: Props) => {
    const { t } = useTranslation();
    const { open } = useWindows();

    const handleOpen = useCallback(() => {
        open<PasteRecordsDialogData>({
            title: t("pasteRecords.title", "Paste JSON records"),
            kind: WindowKind.pasteRecords,
            isModal: true,
            meta: { sourceId, onRowsAdded, defaultVariables },
            layoutData: { width: 640 },
        });
    }, [open, t, sourceId, onRowsAdded, defaultVariables]);

    return (
        <Button
            size="small"
            variant={variant}
            startIcon={<ContentPasteIcon fontSize="small" />}
            onClick={handleOpen}
            disabled={disabled}
            sx={{ textTransform: "none" }}
        >
            {t("pasteRecords.button", "Paste JSON lines")}
        </Button>
    );
};
