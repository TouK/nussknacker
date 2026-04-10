import { Box, Chip, CircularProgress, FormControl, MenuItem, Select, Tooltip, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import type { VariableTypes } from "../../../../../../types/validation";
import { FormRow } from "./components/FormRow";
import { nuMenuProps, nuSelectSx } from "./components/nuInputSx";
import { getTypeColor } from "./components/typeColors";
import type { InputField } from "./types";
import { buildInputFieldsForVariable, EXCLUDED_INPUT_VARIABLES } from "./types";

interface Props {
    fields: InputField[];
    onChange: (fields: InputField[]) => void;
    readOnly?: boolean;
    variableTypes: VariableTypes;
}

function variableHasRecordTime(varName: string, variableTypes: VariableTypes): boolean {
    const typingResult = variableTypes[varName];
    if (!typingResult || !("fields" in typingResult) || !typingResult.fields) return false;
    return "record_time" in (typingResult.fields as Record<string, unknown>);
}

function getAvailableVariables(variableTypes: VariableTypes): string[] {
    return Object.entries(variableTypes)
        .filter(
            ([name, v]) =>
                !EXCLUDED_INPUT_VARIABLES.has(name) &&
                "fields" in (v as object) &&
                (v as { fields?: unknown }).fields &&
                Object.keys((v as { fields: object }).fields).length > 0,
        )
        .map(([name]) => name);
}

export function InputFieldsSection({ fields, onChange, readOnly, variableTypes }: Props) {
    const { t } = useTranslation();

    const availableVars = getAvailableVariables(variableTypes);
    const currentVar = fields[0]?.source.split(".")[0] ?? "";
    const hasConflictingRecordTime = currentVar ? variableHasRecordTime(currentVar, variableTypes) : false;
    const isLoadingTypes =
        availableVars.length === 0 &&
        Object.keys(variableTypes).some((k) => !EXCLUDED_INPUT_VARIABLES.has(k)) &&
        Object.values(variableTypes).every((v) => !("fields" in (v as object)));

    const handleVarChange = (varName: string) => {
        if (varName) onChange(buildInputFieldsForVariable(varName, variableTypes));
    };

    return (
        <FormRow
            label={t("flinkSql.inputFields.variable", "Input variable:")}
            tooltip={t(
                "flinkSql.inputFields.tooltip",
                "The stream variable whose fields are used as the input source. Select a variable to choose which fields are projected into the query.",
            )}
        >
            <Box>
                {isLoadingTypes ? (
                    <Box display="flex" alignItems="center" gap={1} height={35}>
                        <CircularProgress size={14} />
                        <Typography variant="body2" color="text.disabled" sx={{ fontSize: "0.85rem" }}>
                            {t("flinkSql.inputFields.loadingTypes", "Loading variable types\u2026")}
                        </Typography>
                    </Box>
                ) : (
                    <FormControl size="small" variant="outlined" fullWidth>
                        <Select
                            value={currentVar}
                            onChange={(e) => handleVarChange(e.target.value)}
                            disabled={readOnly || availableVars.length === 0}
                            displayEmpty
                            renderValue={(v) =>
                                v ? (
                                    `#${v}`
                                ) : (
                                    <Typography variant="body2" color="text.disabled" sx={{ fontSize: "0.85rem" }}>
                                        {t("flinkSql.inputFields.placeholder", "Select variable\u2026")}
                                    </Typography>
                                )
                            }
                            MenuProps={nuMenuProps}
                            sx={nuSelectSx}
                        >
                            {availableVars.map((v) => (
                                <MenuItem key={v} value={v} sx={{ fontSize: "0.85rem" }}>
                                    #{v}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                )}

                {/* Field chips */}
                {fields.length > 0 && (
                    <Box display="flex" flexWrap="wrap" gap={0.5} mt={0.75}>
                        {fields.map((f) => (
                            <Chip
                                key={f.name}
                                label={f.alias}
                                size="small"
                                sx={{
                                    fontSize: "0.7rem",
                                    fontWeight: 500,
                                    height: 20,
                                    backgroundColor: `${getTypeColor(f.type)}22`,
                                    borderColor: getTypeColor(f.type),
                                    border: `1px solid ${getTypeColor(f.type)}66`,
                                    color: getTypeColor(f.type),
                                }}
                            />
                        ))}
                        {hasConflictingRecordTime ? (
                            <Tooltip
                                title={t(
                                    "flinkSql.inputFields.recordTimeConflict",
                                    "record_time is always added automatically and cannot be projected from the input variable.",
                                )}
                                placement="top"
                            >
                                <Chip
                                    label="record_time"
                                    size="small"
                                    sx={{
                                        fontSize: "0.7rem",
                                        fontWeight: 500,
                                        height: 20,
                                        backgroundColor: "action.disabledBackground",
                                        border: "1px solid",
                                        borderColor: "divider",
                                        color: "text.disabled",
                                        textDecoration: "line-through",
                                        cursor: "default",
                                    }}
                                />
                            </Tooltip>
                        ) : (
                            <Typography
                                variant="caption"
                                sx={{ fontSize: "0.68rem", color: "text.disabled", alignSelf: "center", ml: 0.5 }}
                            >
                                {t("flinkSql.inputFields.recordTime", "+ record_time")}
                            </Typography>
                        )}
                    </Box>
                )}
            </Box>
        </FormRow>
    );
}
