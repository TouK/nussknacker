import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { alpha, Box, Button, Typography } from "@mui/material";
import React from "react";
import { Trans, useTranslation } from "react-i18next";

interface TableFooterProps {
    selectedCount: number;
    allRowsNumber: number;
    handleRemoveRows: () => void;
    handleSelectAll?: () => void;
}

export const TableFooter: React.FC<TableFooterProps> = ({ selectedCount, allRowsNumber, handleRemoveRows, handleSelectAll }) => {
    const { t } = useTranslation();

    return (
        <Box
            sx={(theme) => ({
                display: "flex",
                alignItems: "center",
                px: 2,
                py: 0.5,
                flexShrink: 0,
                background: alpha(theme.palette.primary.main, 0.12),
                borderTop: `1px solid ${alpha(theme.palette.primary.main, 0.25)}`,
            })}
            data-testid="table-footer"
        >
            <Box display="flex" alignItems="center" gap={1} sx={{ flexWrap: "nowrap", whiteSpace: "nowrap" }}>
                <Typography variant="body2" color="textPrimary">
                    <Trans
                        i18nKey="tableFooter.selected"
                        defaults="<strong>{{selectedCount}}</strong> of <strong>{{allRowsNumber}}</strong> selected"
                        values={{ selectedCount, allRowsNumber }}
                        components={{ strong: <strong /> }}
                    />
                </Typography>
                {handleSelectAll && (
                    <>
                        <Typography variant="body2" color="textSecondary" sx={{ opacity: 0.4 }}>
                            ·
                        </Typography>
                        <Button
                            variant="text"
                            size="small"
                            onClick={handleSelectAll}
                            sx={{
                                p: 0,
                                minWidth: 0,
                                fontSize: "inherit",
                                textTransform: "none",
                                color: "text.primary",
                                textDecoration: "underline",
                            }}
                        >
                            {t("tableFooter.selectAll", "Select all")}
                        </Button>
                    </>
                )}
            </Box>
            <Box flex={1} />
            <Button
                variant="text"
                size="small"
                color="error"
                startIcon={<DeleteOutlineIcon fontSize="small" />}
                onClick={handleRemoveRows}
                sx={{ textTransform: "none" }}
            >
                {t("testingDataRecords.tableFooter.remove", "Remove selected")}
            </Button>
        </Box>
    );
};
