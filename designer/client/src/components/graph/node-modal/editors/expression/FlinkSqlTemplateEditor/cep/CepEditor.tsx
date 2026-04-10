import EditIcon from "@mui/icons-material/Edit";
import { Box, Chip, FormControl, IconButton, InputAdornment, MenuItem, Select, TextField, Tooltip, Typography } from "@mui/material";
import React, { useCallback, useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getBorderColor } from "../../../../../../../containers/theme/helpers";
import { useWindows } from "../../../../../../../windowManager/useWindows";
import { WindowKind } from "../../../../../../../windowManager/WindowKind";
import { DurationField } from "../components/DurationField";
import { FormRow } from "../components/FormRow";
import { nuMenuProps, nuSelectSx } from "../components/nuInputSx";
import { getTypeColor } from "../components/typeColors";
import type { CepState, InputField } from "../types";
import { DefineCard } from "./DefineCard";
import { MatchOptionsPanel } from "./MatchOptionsPanel";
import type { EditCepMeasuresData } from "./MeasuresDialogContent";
import { PatternSequence } from "./PatternSequence";

interface Props {
    fields: InputField[];
    config: CepState;
    onChange: (config: CepState) => void;
    readOnly?: boolean;
    outputVar?: string;
    onOutputVarChange?: (name: string) => void;
}

export function CepEditor({ fields, config, onChange, readOnly, outputVar, onOutputVarChange }: Props) {
    const { t } = useTranslation();
    const { open } = useWindows();

    const allFields = useMemo(() => [...fields.filter((f) => f.selected).map((f) => f.alias), "record_time"], [fields]);
    const patternVariableNames = config.pattern.map((pv) => pv.name);

    const handleOpenMeasures = useCallback(() => {
        open<EditCepMeasuresData>({
            title: t("flinkSql.cep.measuresDialogTitle", "Define Match Recognize measures"),
            kind: WindowKind.editCepMeasures,
            isModal: true,
            meta: {
                measures: config.measures,
                patternVariables: config.pattern,
                fields: allFields,
                outputVar,
                onApply: (measures: CepState["measures"]) => onChange({ ...config, measures }),
            },
            layoutData: { width: 640 },
        });
    }, [open, t, config, allFields, onChange, outputVar]);

    const fieldTypeMap = Object.fromEntries(fields.filter((f) => f.selected).map((f) => [f.alias, f.type]));
    const measuresWithColor = config.measures
        .filter((m) => m.alias)
        .map((m) => {
            const inner = m.expression.match(/^\w+\(([^)]+)\)$/)?.[1] ?? m.expression;
            const fieldName = inner.includes(".") ? inner.split(".").slice(1).join(".") : inner;
            const color = getTypeColor(fieldTypeMap[fieldName] ?? "Any");
            return { alias: m.alias, color };
        });

    return (
        <Box display="flex" flexDirection="column">
            {/* Partition By */}
            <FormRow
                label={t("flinkSql.cep.partitionBy", "Partition by:")}
                alignItems="center"
                tooltip={t(
                    "flinkSql.cep.tooltip.partitionBy",
                    "Divides input into independent groups for pattern matching. Without PARTITION BY the operator runs on a single thread and processes all events sequentially.",
                )}
            >
                <FormControl size="small" variant="outlined" fullWidth>
                    <Select
                        value={config.partitionBy}
                        onChange={(e) => onChange({ ...config, partitionBy: e.target.value })}
                        disabled={readOnly || allFields.length === 0}
                        displayEmpty
                        renderValue={(v) =>
                            v || (
                                <Typography variant="body2" color="text.disabled" sx={{ fontSize: "0.8rem" }}>
                                    {t("flinkSql.cep.partitionByPlaceholder", "Select field\u2026")}
                                </Typography>
                            )
                        }
                        MenuProps={nuMenuProps}
                        sx={nuSelectSx}
                    >
                        {allFields.map((f) => (
                            <MenuItem key={f} value={f}>
                                {f}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </FormRow>

            {/* Order By */}
            <FormRow
                label={t("flinkSql.cep.orderBy", "Order by:")}
                alignItems="center"
                tooltip={t(
                    "flinkSql.cep.tooltip.orderBy",
                    "Defines event ordering within each partition. Must be a time attribute (e.g. record_time) in ascending order — Flink requires this for deterministic pattern matching.",
                )}
            >
                <FormControl size="small" variant="outlined" fullWidth>
                    <Select
                        value={config.orderBy}
                        onChange={(e) => onChange({ ...config, orderBy: e.target.value })}
                        disabled={readOnly || allFields.length === 0}
                        displayEmpty
                        renderValue={(v) =>
                            v || (
                                <Typography variant="body2" color="text.disabled" sx={{ fontSize: "0.8rem" }}>
                                    {t("flinkSql.cep.orderByPlaceholder", "Select field\u2026")}
                                </Typography>
                            )
                        }
                        MenuProps={nuMenuProps}
                        sx={nuSelectSx}
                    >
                        {allFields.map((f) => (
                            <MenuItem key={f} value={f}>
                                {f}
                            </MenuItem>
                        ))}
                    </Select>
                </FormControl>
            </FormRow>

            {/* Pattern sequence */}
            <FormRow
                label={t("flinkSql.cep.patternSequence", "Pattern:")}
                tooltip={t(
                    "flinkSql.cep.tooltip.pattern",
                    "Regular expression over event sequences. Each variable (A, B, C) represents a type of event defined in the DEFINE section. Quantifiers control how many consecutive events match each variable.",
                )}
            >
                <PatternSequence pattern={config.pattern} onChange={(pattern) => onChange({ ...config, pattern })} readOnly={readOnly} />
            </FormRow>

            {/* Define cards — indented to align with value column */}
            {config.pattern.length > 0 && (
                <Box sx={{ paddingLeft: "20%" }} display="flex" flexDirection="column" gap={0.5} mt={0.5}>
                    {config.pattern.map((pv, i) => (
                        <DefineCard
                            key={pv.name}
                            variable={pv}
                            fields={allFields}
                            onChange={(updated) => {
                                const next = config.pattern.map((p, idx) => (idx === i ? updated : p));
                                onChange({ ...config, pattern: next });
                            }}
                            readOnly={readOnly}
                        />
                    ))}
                </Box>
            )}

            {/* Match options */}
            <FormRow
                label={t("flinkSql.cep.matchOptions", "Match options:")}
                tooltip={t(
                    "flinkSql.cep.tooltip.matchOptions",
                    "Controls how many rows are emitted per match and where matching resumes after a match is found.",
                )}
            >
                <MatchOptionsPanel
                    options={config.matchOptions}
                    onChange={(matchOptions) => onChange({ ...config, matchOptions })}
                    patternVariableNames={patternVariableNames}
                    readOnly={readOnly}
                />
            </FormRow>

            {/* Within clause */}
            <FormRow
                label={t("flinkSql.cep.within", "Within:")}
                alignItems="center"
                tooltip={t(
                    "flinkSql.cep.tooltip.within",
                    "Maximum time allowed between the first and last event of a match. Flink uses this to prune state — strongly recommended for long-running queries. Leave empty for no time constraint.",
                )}
            >
                <DurationField value={config.within} onChange={(within) => onChange({ ...config, within })} disabled={readOnly} optional />
            </FormRow>

            {/* Output variable — mirrors Input variable row */}
            <FormRow
                label={t("flinkSql.cep.outputVar", "Output variable:")}
                alignItems="flex-start"
                tooltip={t(
                    "flinkSql.cep.tooltip.outputVar",
                    "Name of the output record in the process function. Measure aliases become fields on this variable (e.g. #outputVar.fieldName).",
                )}
            >
                <Box>
                    <TextField
                        size="small"
                        value={outputVar ?? ""}
                        onChange={(e) => onOutputVarChange?.(e.target.value)}
                        disabled={readOnly || !onOutputVarChange}
                        placeholder={t("flinkSql.cep.outputVarPlaceholder", "output variable name")}
                        fullWidth
                        sx={(theme) => ({
                            "& .MuiOutlinedInput-root": {
                                borderRadius: 0,
                                backgroundColor: "background.paper",
                                fontSize: "0.85rem",
                                height: 35,
                                outline: `1px solid ${getBorderColor(theme)}`,
                                "&:focus-within": { outline: `1px solid ${theme.palette.primary.main}` },
                                "& fieldset": { border: "none" },
                            },
                        })}
                        InputProps={
                            !readOnly
                                ? {
                                      endAdornment: (
                                          <InputAdornment position="end">
                                              <Tooltip title={t("flinkSql.cep.editMeasures", "Edit measures")}>
                                                  <IconButton size="small" onClick={handleOpenMeasures} edge="end" sx={{ mr: 0.25 }}>
                                                      <EditIcon sx={{ fontSize: "0.95rem" }} />
                                                  </IconButton>
                                              </Tooltip>
                                          </InputAdornment>
                                      ),
                                  }
                                : undefined
                        }
                    />
                    {/* Measure chips */}
                    {measuresWithColor.length > 0 && (
                        <Box display="flex" flexWrap="wrap" gap={0.5} mt={0.75}>
                            {measuresWithColor.map(({ alias, color }) => (
                                <Chip
                                    key={alias}
                                    label={alias}
                                    size="small"
                                    sx={{
                                        fontSize: "0.7rem",
                                        fontWeight: 500,
                                        height: 20,
                                        backgroundColor: `${color}22`,
                                        border: `1px solid ${color}66`,
                                        color,
                                    }}
                                />
                            ))}
                        </Box>
                    )}
                </Box>
            </FormRow>
        </Box>
    );
}
