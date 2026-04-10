import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import {
    Box,
    Button,
    FormControl,
    IconButton,
    MenuItem,
    Select,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import type { Measure, PatternVariable } from "../types";
import { getPatternVariableColor } from "../types";

interface Props {
    measures: Measure[];
    pattern: PatternVariable[];
    onChange: (measures: Measure[]) => void;
    readOnly?: boolean;
}

export function MeasuresTable({ measures, pattern, onChange, readOnly }: Props) {
    const { t } = useTranslation();

    const handleChange = (i: number, update: Partial<Measure>) => {
        onChange(measures.map((m, idx) => (idx === i ? { ...m, ...update } : m)));
    };

    const handleAdd = () => {
        onChange([...measures, { variable: pattern[0]?.name ?? "", func: "", expression: "", alias: "" }]);
    };

    const handleDelete = (i: number) => {
        onChange(measures.filter((_, idx) => idx !== i));
    };

    return (
        <Box
            sx={(theme) => ({
                border: `1px solid ${theme.palette.divider}`,
                borderRadius: 1.5,
                overflow: "hidden",
            })}
        >
            {/* Table header */}
            <Box
                sx={(theme) => ({
                    display: "flex",
                    gap: 1,
                    px: 1.5,
                    py: 0.75,
                    borderBottom: `1px solid ${theme.palette.divider}`,
                    backgroundColor: "rgba(255,255,255,0.03)",
                })}
            >
                <Typography
                    variant="caption"
                    sx={{
                        flex: "0 0 48px",
                        textTransform: "uppercase",
                        letterSpacing: "0.08em",
                        fontWeight: 600,
                        fontSize: "0.6rem",
                        color: "text.secondary",
                    }}
                >
                    {t("flinkSql.cep.measuresVar", "Var")}
                </Typography>
                <Typography
                    variant="caption"
                    sx={{
                        flex: "0 0 100px",
                        textTransform: "uppercase",
                        letterSpacing: "0.08em",
                        fontWeight: 600,
                        fontSize: "0.6rem",
                        color: "text.secondary",
                    }}
                >
                    {t("flinkSql.cep.measuresFunc", "Func")}
                </Typography>
                <Typography
                    variant="caption"
                    sx={{
                        flex: 1,
                        textTransform: "uppercase",
                        letterSpacing: "0.08em",
                        fontWeight: 600,
                        fontSize: "0.6rem",
                        color: "text.secondary",
                    }}
                >
                    {t("flinkSql.cep.measuresExpr", "Expression")}
                </Typography>
                <Typography
                    variant="caption"
                    sx={{
                        flex: 1,
                        textTransform: "uppercase",
                        letterSpacing: "0.08em",
                        fontWeight: 600,
                        fontSize: "0.6rem",
                        color: "text.secondary",
                    }}
                >
                    {t("flinkSql.cep.measuresAlias", "Alias")}
                </Typography>
                <Box sx={{ width: 28 }} />
            </Box>

            {/* Table rows */}
            {measures.map((m, i) => (
                <Box
                    key={i}
                    sx={(theme) => ({
                        display: "flex",
                        alignItems: "center",
                        gap: 1,
                        px: 1.5,
                        py: 0.75,
                        "&:not(:last-child)": {
                            borderBottom: `1px solid ${theme.palette.divider}`,
                        },
                    })}
                >
                    {/* VAR column — colored circle */}
                    <Box sx={{ flex: "0 0 48px", display: "flex", justifyContent: "center" }}>
                        <FormControl size="small" variant="outlined" sx={{ width: 40 }}>
                            <Select
                                value={m.variable}
                                onChange={(e) => handleChange(i, { variable: e.target.value })}
                                disabled={readOnly}
                                variant="standard"
                                disableUnderline
                                renderValue={(v) => (
                                    <Box
                                        sx={{
                                            width: 22,
                                            height: 22,
                                            borderRadius: "50%",
                                            backgroundColor: getPatternVariableColor(v),
                                            color: "#fff",
                                            fontSize: "0.7rem",
                                            fontWeight: 700,
                                            display: "flex",
                                            alignItems: "center",
                                            justifyContent: "center",
                                        }}
                                    >
                                        {v}
                                    </Box>
                                )}
                                sx={{ "& .MuiSelect-select": { py: 0.25, display: "flex", alignItems: "center" } }}
                            >
                                {pattern.map((pv) => (
                                    <MenuItem key={pv.name} value={pv.name}>
                                        <Box display="flex" alignItems="center" gap={1}>
                                            <Box
                                                sx={{
                                                    width: 16,
                                                    height: 16,
                                                    borderRadius: "50%",
                                                    backgroundColor: getPatternVariableColor(pv.name),
                                                    flexShrink: 0,
                                                }}
                                            />
                                            {pv.name}
                                        </Box>
                                    </MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                    </Box>

                    {/* FUNC column — inline toggle buttons */}
                    <Box sx={{ flex: "0 0 100px" }}>
                        <ToggleButtonGroup
                            value={m.func}
                            exclusive
                            size="small"
                            onChange={(_, val) => {
                                if (val !== null) handleChange(i, { func: val });
                            }}
                            disabled={readOnly}
                            sx={{
                                height: 26,
                                "& .MuiToggleButton-root": {
                                    fontSize: "0.6rem",
                                    px: 0.75,
                                    py: 0,
                                    textTransform: "none",
                                    fontWeight: 600,
                                    lineHeight: 1,
                                },
                            }}
                        >
                            <ToggleButton value="" disableRipple>
                                {"\u2014"}
                            </ToggleButton>
                            <ToggleButton value="FIRST" disableRipple>
                                FIRST
                            </ToggleButton>
                            <ToggleButton value="LAST" disableRipple>
                                LAST
                            </ToggleButton>
                        </ToggleButtonGroup>
                    </Box>

                    {/* EXPRESSION */}
                    <TextField
                        size="small"
                        value={m.expression}
                        onChange={(e) => handleChange(i, { expression: e.target.value })}
                        placeholder={`${m.variable}.field`}
                        disabled={readOnly}
                        sx={{ flex: 1, "& .MuiInputBase-input": { fontSize: "0.8rem", py: 0.75 } }}
                    />

                    {/* ALIAS */}
                    <TextField
                        size="small"
                        value={m.alias}
                        onChange={(e) => handleChange(i, { alias: e.target.value })}
                        placeholder={t("flinkSql.cep.aliasPlaceholder", "alias")}
                        disabled={readOnly}
                        sx={{ flex: 1, "& .MuiInputBase-input": { fontSize: "0.8rem", py: 0.75 } }}
                    />

                    {/* DELETE */}
                    {!readOnly && (
                        <IconButton size="small" onClick={() => handleDelete(i)} sx={{ p: 0.5 }}>
                            <CloseIcon sx={{ fontSize: "0.9rem" }} />
                        </IconButton>
                    )}
                </Box>
            ))}

            {/* Empty state / Add button */}
            <Box
                sx={(theme) => ({
                    px: 1.5,
                    py: 1,
                    borderTop: measures.length > 0 ? `1px solid ${theme.palette.divider}` : "none",
                })}
            >
                {!readOnly && (
                    <Button
                        size="small"
                        startIcon={<AddIcon sx={{ fontSize: "0.85rem !important" }} />}
                        onClick={handleAdd}
                        sx={{ textTransform: "none", fontSize: "0.72rem", color: "text.secondary" }}
                    >
                        {t("flinkSql.cep.addMeasure", "Add measure")}
                    </Button>
                )}
            </Box>
        </Box>
    );
}
