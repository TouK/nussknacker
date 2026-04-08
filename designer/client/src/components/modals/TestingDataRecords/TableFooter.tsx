import { alpha, Box, Button, Typography } from "@mui/material";
import React from "react";
import { Trans, useTranslation } from "react-i18next";

import Remove from "../../../assets/img/toolbarButtons/archive.svg";
import { getBorderColor } from "../../../containers/theme/helpers";

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
                <Typography variant="body2" color="textSecondary" sx={{ opacity: 0.4 }}>
                    ·
                </Typography>
            </Box>
            <Box display="flex" alignItems="center">
                <Button
                    variant={"text"}
                    sx={(theme) => ({ color: theme.palette.text.primary, textTransform: "capitalize" })}
                    onClick={handleRemoveRows}
                >
                    <Box width={"24px"} height={"24px"}>
                        <Remove />
                    </Box>
                    {t("testingDataRecords.tableFooter.remove", "Remove")}
                </Button>
            </Box>
        </Box>
    );
};
