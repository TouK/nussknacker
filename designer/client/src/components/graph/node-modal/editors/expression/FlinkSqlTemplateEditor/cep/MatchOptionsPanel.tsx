import { Box, FormControl, MenuItem, Select, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { InfoTooltip } from "../../../InfoTooltip/InfoTooltip";
import { nuMenuProps, nuSelectSx } from "../components/nuInputSx";
import type { AfterMatchStrategy, MatchOptions } from "../types";

interface Props {
    options: MatchOptions;
    onChange: (options: MatchOptions) => void;
    patternVariableNames: string[];
    readOnly?: boolean;
}

const AFTER_MATCH_TYPES = ["SKIP PAST LAST ROW", "SKIP TO NEXT ROW", "SKIP TO FIRST", "SKIP TO LAST"] as const;

type AfterMatchType = (typeof AFTER_MATCH_TYPES)[number];

const colLabelSx = {
    fontSize: "0.58rem",
    fontWeight: 700,
    textTransform: "uppercase" as const,
    letterSpacing: "0.08em",
    color: "text.disabled",
    mb: 0.4,
    display: "block",
};

function getAfterMatchType(strategy: AfterMatchStrategy): AfterMatchType {
    return strategy.type;
}

function getAfterMatchVariable(strategy: AfterMatchStrategy): string {
    if (strategy.type === "SKIP TO FIRST" || strategy.type === "SKIP TO LAST") {
        return strategy.variable;
    }
    return "";
}

function needsVariable(type: AfterMatchType): boolean {
    return type === "SKIP TO FIRST" || type === "SKIP TO LAST";
}

export function MatchOptionsPanel({ options, onChange, patternVariableNames, readOnly }: Props) {
    const { t } = useTranslation();
    const afterMatchType = getAfterMatchType(options.afterMatch);
    const afterMatchVariable = getAfterMatchVariable(options.afterMatch);

    const handleRowsPerMatchChange = (value: string) => {
        onChange({ ...options, rowsPerMatch: value as MatchOptions["rowsPerMatch"] });
    };

    const handleAfterMatchTypeChange = (type: string) => {
        const tt = type as AfterMatchType;
        if (needsVariable(tt)) {
            const variable = patternVariableNames[0] ?? "A";
            onChange({ ...options, afterMatch: { type: tt, variable } as AfterMatchStrategy });
        } else {
            onChange({ ...options, afterMatch: { type: tt } as AfterMatchStrategy });
        }
    };

    const handleAfterMatchVariableChange = (variable: string) => {
        if (needsVariable(afterMatchType)) {
            onChange({ ...options, afterMatch: { type: afterMatchType, variable } as AfterMatchStrategy });
        }
    };

    return (
        <Box display="flex" gap={1} flexWrap="wrap">
            <Box flex={1} minWidth={140}>
                <Box display="flex" alignItems="center" gap={0.5} mb={0.4}>
                    <Typography component="span" sx={colLabelSx}>
                        {t("flinkSql.cep.rowsPerMatch", "Rows Per Match")}
                    </Typography>
                    <InfoTooltip
                        title={t(
                            "flinkSql.cep.tooltip.rowsPerMatch",
                            "ONE ROW PER MATCH: emits one summary row per match (supported). ALL ROWS PER MATCH: emits one row per event in the match (not yet supported in Flink).",
                        )}
                        variant="hover"
                        placement="top"
                    />
                </Box>
                <FormControl size="small" variant="outlined" fullWidth>
                    <Select
                        value={options.rowsPerMatch}
                        onChange={(e) => handleRowsPerMatchChange(e.target.value)}
                        disabled={readOnly}
                        MenuProps={nuMenuProps}
                        sx={nuSelectSx}
                    >
                        <MenuItem value="ONE ROW PER MATCH">ONE ROW PER MATCH</MenuItem>
                        <MenuItem value="ALL ROWS PER MATCH">ALL ROWS PER MATCH</MenuItem>
                    </Select>
                </FormControl>
            </Box>

            <Box flex={1} minWidth={140}>
                <Box display="flex" alignItems="center" gap={0.5} mb={0.4}>
                    <Typography component="span" sx={colLabelSx}>
                        {t("flinkSql.cep.afterMatch", "After Match")}
                    </Typography>
                    <InfoTooltip
                        title={t(
                            "flinkSql.cep.tooltip.afterMatch",
                            "Determines where matching resumes after a match is found. SKIP PAST LAST ROW: after the last matched event (no overlapping matches). SKIP TO NEXT ROW: after the first matched event. SKIP TO FIRST/LAST var: at the first/last event of the given pattern variable.",
                        )}
                        variant="hover"
                        placement="top"
                    />
                </Box>
                <Box display="flex" gap={0.5}>
                    <FormControl size="small" variant="outlined" sx={{ flex: 1 }}>
                        <Select
                            value={afterMatchType}
                            onChange={(e) => handleAfterMatchTypeChange(e.target.value)}
                            disabled={readOnly}
                            MenuProps={nuMenuProps}
                            sx={nuSelectSx}
                        >
                            {AFTER_MATCH_TYPES.map((type) => (
                                <MenuItem key={type} value={type}>
                                    {type}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>

                    {needsVariable(afterMatchType) && (
                        <FormControl size="small" variant="outlined" sx={{ minWidth: 60 }}>
                            <Select
                                value={afterMatchVariable}
                                onChange={(e) => handleAfterMatchVariableChange(e.target.value)}
                                disabled={readOnly}
                                MenuProps={nuMenuProps}
                                sx={nuSelectSx}
                            >
                                {patternVariableNames.map((name) => (
                                    <MenuItem key={name} value={name}>
                                        {name}
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    )}
                </Box>
            </Box>
        </Box>
    );
}
