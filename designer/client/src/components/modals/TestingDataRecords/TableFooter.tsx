import type { GridSelection } from "@glideapps/glide-data-grid";
import { alpha, Box, Button, Typography } from "@mui/material";
import React from "react";
import { Trans, useTranslation } from "react-i18next";

import Remove from "../../../assets/img/toolbarButtons/archive.svg";
import { getBorderColor } from "../../../containers/theme/helpers";

interface TableFooterProps {
    selection: GridSelection;
    allRowsNumber: number;
    onDeleteRows: (rows: number[]) => void;
    clearSelection: () => void;
}

export const TableFooter: React.FC<TableFooterProps> = ({ selection, allRowsNumber, onDeleteRows, clearSelection }) => {
    const { t } = useTranslation();

    const rowsFromSelection = React.useMemo(() => {
        if (!selection) return [];
        const rowsArr = selection.rows && typeof selection.rows.toArray === "function" ? selection.rows.toArray() : [];
        if (rowsArr.length > 0) return rowsArr.slice().sort((a, b) => a - b);

        const range = selection.current?.range;
        if (range && typeof range.y === "number" && typeof range.height === "number") {
            return Array.from({ length: range.height }, (_, i) => range.y + i);
        }

        return [];
    }, [selection]);

    const selectedCount = rowsFromSelection.length;

    const handleRemove = () => {
        if (!onDeleteRows || rowsFromSelection.length === 0) return;
        onDeleteRows(rowsFromSelection);
        clearSelection();
    };

    return (
        <Box
            sx={(theme) => ({
                position: "absolute",
                bottom: "20px",
                left: 0,
                right: 0,
                margin: "0 auto",
                width: "320px",
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                px: 2,
                py: 1,
                background: alpha(theme.palette.primary.main, 0.25),
                border: `1px solid ${getBorderColor(theme)}`,
                boxShadow: "0 0 2px rgba(0,0,0,0.8),0 0 20px rgba(0,0,0,0.8)",
                backdropFilter: "blur(20px)",
            })}
            data-testid="table-footer"
        >
            <Typography variant="body2" color="textPrimary">
                <Trans
                    i18nKey="tableFooter.selected"
                    defaults="<strong>{{selectedCount}}</strong> of <strong>{{allRowsNumber}}</strong> selected"
                    values={{ selectedCount, allRowsNumber }}
                    components={{ strong: <strong /> }}
                />
            </Typography>
            <Button
                variant={"text"}
                sx={(theme) => ({ color: theme.palette.text.primary, textTransform: "capitalize" })}
                onClick={handleRemove}
            >
                <Box width={"24px"} height={"24px"}>
                    <Remove />
                </Box>
                {t("testingDataRecords.tableFooter.remove", "Remove")}
            </Button>
        </Box>
    );
};
