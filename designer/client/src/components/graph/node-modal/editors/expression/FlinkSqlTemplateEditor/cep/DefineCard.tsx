import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import { Autocomplete, Box, Button, FormControl, IconButton, MenuItem, Select, TextField, Typography } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import type { Condition, PatternVariable } from "../types";
import { CONDITION_OPERATORS, getPatternVariableColor } from "../types";

interface Props {
    variable: PatternVariable;
    fields: string[];
    onChange: (variable: PatternVariable) => void;
    readOnly?: boolean;
}

function conditionToExpr(cond: Condition): string {
    if (cond.mode === "expr") return cond.expression;
    if (cond.operator === "IS NULL" || cond.operator === "IS NOT NULL") {
        return `${cond.field} ${cond.operator}`.trim();
    }
    return `${cond.field} ${cond.operator} ${cond.value}`.trim();
}

function tryParseSimple(expression: string): { field: string; operator: string; value: string } | null {
    const trimmed = expression.trim();
    const isNull = trimmed.match(/^(\S+)\s+(IS\s+NOT\s+NULL|IS\s+NULL)\s*$/i);
    if (isNull) return { field: isNull[1], operator: isNull[2].replace(/\s+/g, " ").toUpperCase(), value: "" };
    const op = trimmed.match(/^(\S+)\s*(!=|<=|>=|=|<|>)\s*(.+)/);
    if (op) return { field: op[1], operator: op[2], value: op[3].trim() };
    return null;
}

const modeToggleBase = {
    px: 0.75,
    py: 0.15,
    fontSize: "0.6rem",
    fontWeight: 600,
    cursor: "pointer",
    userSelect: "none" as const,
    lineHeight: 1.6,
};

export function DefineCard({ variable, fields, onChange, readOnly }: Props) {
    const { t } = useTranslation();
    const color = getPatternVariableColor(variable.name);
    const exprSuggestions = fields.map((f) => `${variable.name}.${f}`);

    const handleConditionChange = (i: number, updated: Condition) => {
        const next = variable.conditions.map((c, idx) => (idx === i ? updated : c));
        onChange({ ...variable, conditions: next });
    };

    const handleSimpleFieldChange = (i: number, cond: Condition, update: Partial<{ field: string; operator: string; value: string }>) => {
        if (cond.mode !== "simple") return;
        handleConditionChange(i, { ...cond, ...update });
    };

    const handleAddCondition = () => {
        onChange({ ...variable, conditions: [...variable.conditions, { mode: "simple", field: "", operator: "=", value: "" }] });
    };

    const handleDeleteCondition = (i: number) => {
        onChange({ ...variable, conditions: variable.conditions.filter((_, idx) => idx !== i) });
    };

    const handleModeSwitch = (i: number, cond: Condition, newMode: "simple" | "expr") => {
        if (cond.mode === newMode) return;
        if (newMode === "expr") {
            handleConditionChange(i, { mode: "expr", expression: cond.mode === "simple" ? conditionToExpr(cond) : "" });
        } else if (cond.mode === "expr") {
            const parsed = tryParseSimple(cond.expression);
            handleConditionChange(i, { mode: "simple", ...(parsed ?? { field: "", operator: "=", value: "" }) });
        }
    };

    return (
        <Box
            sx={(theme) => ({
                borderLeft: `3px solid ${color}`,
                borderRadius: "0 4px 4px 0",
                border: `1px solid ${theme.palette.divider}`,
                borderLeftWidth: 3,
                borderLeftColor: color,
                overflow: "hidden",
            })}
        >
            {/* Compact header: variable letter + description + quantifier */}
            <Box
                sx={(theme) => ({
                    px: 1,
                    py: 0.4,
                    borderBottom: `1px solid ${theme.palette.divider}`,
                    display: "flex",
                    alignItems: "center",
                    gap: 0.75,
                    backgroundColor: `${color}18`,
                })}
            >
                <Typography
                    sx={{
                        fontSize: "0.78rem",
                        fontWeight: 700,
                        color: color,
                        flexShrink: 0,
                        lineHeight: 1,
                    }}
                >
                    {variable.name}
                </Typography>
                <Typography variant="caption" sx={{ flex: 1, fontSize: "0.72rem", color: "text.secondary" }}>
                    {variable.description || ""}
                </Typography>
                {variable.quantifier && (
                    <Typography
                        variant="caption"
                        sx={{
                            px: 0.5,
                            py: 0.05,
                            borderRadius: 0.5,
                            backgroundColor: "rgba(255,255,255,0.08)",
                            color: "text.secondary",
                            fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                            fontSize: "0.6rem",
                            fontWeight: 600,
                        }}
                    >
                        {variable.quantifier}
                    </Typography>
                )}
            </Box>

            {/* Conditions */}
            <Box px={1} py={0.25} display="flex" flexDirection="column" gap={0}>
                {variable.conditions.map((cond, i) => (
                    <React.Fragment key={i}>
                        <Box display="flex" alignItems="center" gap={0.5}>
                            {/* AND label */}
                            {i > 0 ? (
                                <Typography
                                    variant="caption"
                                    sx={{
                                        color: "text.disabled",
                                        fontSize: "0.58rem",
                                        fontWeight: 700,
                                        textTransform: "uppercase",
                                        letterSpacing: "0.04em",
                                        width: 24,
                                        flexShrink: 0,
                                        textAlign: "center",
                                    }}
                                >
                                    AND
                                </Typography>
                            ) : (
                                <Box sx={{ width: 24, flexShrink: 0 }} />
                            )}

                            {/* simple | expr toggle */}
                            <Box sx={{ display: "flex", flexShrink: 0 }}>
                                <Box
                                    onClick={readOnly ? undefined : () => handleModeSwitch(i, cond, "simple")}
                                    sx={(theme) => ({
                                        ...modeToggleBase,
                                        cursor: readOnly ? "default" : "pointer",
                                        border: `1px solid ${theme.palette.divider}`,
                                        borderRight: "none",
                                        borderRadius: "3px 0 0 3px",
                                        backgroundColor: cond.mode === "simple" ? "#7c4dff" : "transparent",
                                        color: cond.mode === "simple" ? "#fff" : theme.palette.text.disabled,
                                    })}
                                >
                                    simple
                                </Box>
                                <Box
                                    onClick={readOnly ? undefined : () => handleModeSwitch(i, cond, "expr")}
                                    sx={(theme) => ({
                                        ...modeToggleBase,
                                        cursor: readOnly ? "default" : "pointer",
                                        border: `1px solid ${theme.palette.divider}`,
                                        borderRadius: "0 3px 3px 0",
                                        backgroundColor: cond.mode === "expr" ? "#7c4dff" : "transparent",
                                        color: cond.mode === "expr" ? "#fff" : theme.palette.text.disabled,
                                    })}
                                >
                                    expr
                                </Box>
                            </Box>

                            {cond.mode === "expr" ? (
                                <Autocomplete
                                    freeSolo
                                    options={exprSuggestions}
                                    value={cond.expression}
                                    onInputChange={(_, value) => handleConditionChange(i, { mode: "expr", expression: value })}
                                    disabled={readOnly}
                                    sx={{ flex: 1 }}
                                    renderInput={(params) => (
                                        <TextField
                                            {...params}
                                            size="small"
                                            placeholder="expression..."
                                            sx={{
                                                "& .MuiOutlinedInput-root": {
                                                    borderRadius: 0,
                                                    backgroundColor: "background.paper",
                                                    fontSize: "0.78rem",
                                                    fontFamily: "monospace",
                                                    height: 28,
                                                    "& fieldset": { border: "none" },
                                                    "&:hover fieldset": { border: "none" },
                                                    "&.Mui-focused fieldset": { border: "none" },
                                                    borderBottom: "1px solid rgba(255,255,255,0.12)",
                                                },
                                                "& .MuiInputBase-input": { py: "4px" },
                                            }}
                                        />
                                    )}
                                />
                            ) : (
                                <>
                                    <FormControl size="small" variant="outlined" sx={{ minWidth: 90, flex: 1 }}>
                                        <Select
                                            value={cond.field}
                                            onChange={(e) => handleSimpleFieldChange(i, cond, { field: e.target.value })}
                                            disabled={readOnly || fields.length === 0}
                                            displayEmpty
                                            renderValue={(v) =>
                                                v || (
                                                    <Typography variant="body2" color="text.disabled" sx={{ fontSize: "0.75rem" }}>
                                                        {t("flinkSql.cep.fieldPlaceholder", "field")}
                                                    </Typography>
                                                )
                                            }
                                            sx={{
                                                fontSize: "0.78rem",
                                                borderRadius: 0,
                                                backgroundColor: "background.paper",
                                                height: 28,
                                                "& .MuiSelect-select": { py: "4px !important" },
                                                "& .MuiOutlinedInput-notchedOutline": { border: "none" },
                                                "&:hover .MuiOutlinedInput-notchedOutline": { border: "none" },
                                                "&.Mui-focused .MuiOutlinedInput-notchedOutline": { border: "none" },
                                                borderBottom: "1px solid rgba(255,255,255,0.12)",
                                            }}
                                        >
                                            {fields.map((f) => (
                                                <MenuItem key={f} value={f} sx={{ fontSize: "0.78rem" }}>
                                                    {f}
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </FormControl>

                                    <Box
                                        sx={{
                                            px: 0.75,
                                            flexShrink: 0,
                                            backgroundColor: "rgba(255,255,255,0.06)",
                                            display: "flex",
                                            alignItems: "center",
                                            height: 28,
                                        }}
                                    >
                                        <FormControl size="small" variant="outlined" sx={{ minWidth: 52 }}>
                                            <Select
                                                value={cond.operator}
                                                onChange={(e) => handleSimpleFieldChange(i, cond, { operator: e.target.value })}
                                                disabled={readOnly}
                                                variant="standard"
                                                disableUnderline
                                                sx={{
                                                    fontSize: "0.72rem",
                                                    fontWeight: 600,
                                                    "& .MuiSelect-select": { py: 0, px: 0.25 },
                                                }}
                                            >
                                                {CONDITION_OPERATORS.map((op) => (
                                                    <MenuItem key={op} value={op} sx={{ fontSize: "0.78rem" }}>
                                                        {op}
                                                    </MenuItem>
                                                ))}
                                            </Select>
                                        </FormControl>
                                    </Box>

                                    {cond.operator !== "IS NULL" && cond.operator !== "IS NOT NULL" && (
                                        <TextField
                                            size="small"
                                            value={cond.value}
                                            onChange={(e) => handleSimpleFieldChange(i, cond, { value: e.target.value })}
                                            placeholder={t("flinkSql.cep.valuePlaceholder", "value")}
                                            disabled={readOnly}
                                            sx={{
                                                flex: 1,
                                                "& .MuiOutlinedInput-root": {
                                                    borderRadius: 0,
                                                    backgroundColor: "background.paper",
                                                    fontSize: "0.78rem",
                                                    height: 28,
                                                    "& fieldset": { border: "none" },
                                                    "&:hover fieldset": { border: "none" },
                                                    "&.Mui-focused fieldset": { border: "none" },
                                                    borderBottom: "1px solid rgba(255,255,255,0.12)",
                                                },
                                                "& .MuiInputBase-input": { py: "4px" },
                                            }}
                                        />
                                    )}
                                </>
                            )}

                            {!readOnly && variable.conditions.length > 1 && (
                                <IconButton size="small" onClick={() => handleDeleteCondition(i)} sx={{ p: 0.25, flexShrink: 0 }}>
                                    <CloseIcon sx={{ fontSize: "0.8rem" }} />
                                </IconButton>
                            )}
                        </Box>
                    </React.Fragment>
                ))}

                {!readOnly && (
                    <Button
                        size="small"
                        startIcon={<AddIcon sx={{ fontSize: "0.75rem !important" }} />}
                        onClick={handleAddCondition}
                        sx={{
                            textTransform: "none",
                            alignSelf: "flex-start",
                            fontSize: "0.68rem",
                            mt: 0.25,
                            py: 0.25,
                            color: "text.disabled",
                            minWidth: 0,
                        }}
                    >
                        {t("flinkSql.cep.addCondition", "Add condition")}
                    </Button>
                )}
            </Box>
        </Box>
    );
}
