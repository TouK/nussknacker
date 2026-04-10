import { Box, Button, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { SectionHeader } from "../components/SectionHeader";
import type { CepState } from "../types";

interface Props {
    config: CepState;
    onOpenMeasures: () => void;
    readOnly?: boolean;
}

export function OutputSummary({ config, onOpenMeasures, readOnly }: Props) {
    const { t } = useTranslation();

    const measureCount = config.measures.length;
    const aliasNames = config.measures.map((m) => m.alias).filter(Boolean);
    const preview = aliasNames.slice(0, 3).join(", ") + (aliasNames.length > 3 ? ", \u2026" : "");
    const summaryText =
        measureCount > 0 ? `${measureCount} measures defined (${preview})` : t("flinkSql.cep.noMeasures", "No measures defined yet.");

    return (
        <Box display="flex" flexDirection="column" gap={1.5}>
            <SectionHeader
                number={3}
                title={t("flinkSql.cep.outputTitle", "Output")}
                subtitle={t("flinkSql.cep.outputSubtitle", "measures emitted per match")}
                rightContent={
                    <Typography
                        variant="caption"
                        sx={(theme) => ({
                            px: 1,
                            py: 0.25,
                            borderRadius: 1,
                            backgroundColor: theme.palette.action.selected,
                            color: theme.palette.text.secondary,
                            fontSize: "0.7rem",
                            fontWeight: 500,
                        })}
                    >
                        {measureCount} measures
                    </Typography>
                }
            />

            <Box
                sx={(theme) => ({
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 1.5,
                    p: 1.5,
                    display: "flex",
                    alignItems: "center",
                    gap: 2,
                })}
            >
                <Typography variant="caption" color="text.secondary" sx={{ fontSize: "0.75rem", flex: 1 }}>
                    {summaryText}
                </Typography>

                {!readOnly && (
                    <Button
                        size="small"
                        variant="outlined"
                        onClick={onOpenMeasures}
                        sx={{ textTransform: "none", fontSize: "0.78rem", flexShrink: 0 }}
                    >
                        {t("flinkSql.cep.editMeasures", "Edit measures\u2026")}
                    </Button>
                )}
            </Box>
        </Box>
    );
}
