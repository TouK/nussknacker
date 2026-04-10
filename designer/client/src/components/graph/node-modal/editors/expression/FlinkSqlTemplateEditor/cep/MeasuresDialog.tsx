import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import {
    Autocomplete,
    Box,
    Button,
    Dialog,
    DialogActions,
    DialogContent,
    DialogTitle,
    Divider,
    FormControl,
    IconButton,
    MenuItem,
    Select,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Typography,
} from "@mui/material";
import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import type { Measure, PatternVariable } from "../types";
import { getPatternVariableColor } from "../types";

interface Props {
    open: boolean;
    measures: Measure[];
    patternVariables: PatternVariable[];
    fields: string[];
    onApply: (measures: Measure[]) => void;
    onClose: () => void;
}

export function MeasuresDialog({ open, measures, patternVariables, fields, onApply, onClose }: Props) {
    const { t } = useTranslation();
    const [draft, setDraft] = useState<Measure[]>([]);
    const exprSuggestions = patternVariables.flatMap((pv) => fields.map((f) => `${pv.name}.${f}`));

    useEffect(() => {
        if (open) {
            setDraft(measures.map((m) => ({ ...m })));
        }
    }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

    const handleChange = (i: number, update: Partial<Measure>) => {
        setDraft((prev) => prev.map((m, idx) => (idx === i ? { ...m, ...update } : m)));
    };

    const handleAdd = () => {
        setDraft((prev) => [...prev, { variable: patternVariables[0]?.name ?? "", func: "", expression: "", alias: "" }]);
    };

    const handleDelete = (i: number) => {
        setDraft((prev) => prev.filter((_, idx) => idx !== i));
    };

    return (
        <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth PaperProps={{ sx: { maxHeight: "80vh" } }}>
            <DialogTitle sx={{ pb: 1 }}>
                <Typography variant="subtitle1" fontWeight={600}>
                    {t("flinkSql.cep.measuresDialogTitle", "Edit Output Measures")}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                    {t("flinkSql.cep.measuresDialogSubtitle", "Fields emitted for each matched pattern")}
                </Typography>
            </DialogTitle>
            <Divider />
            <DialogContent sx={{ pt: 1.5, pb: 1 }}>
                {/* Table header */}
                <Box
                    sx={(theme) => ({
                        display: "flex",
                        gap: 1,
                        px: 1,
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
                        {t("flinkSql.cep.measuresAlias", "Output Alias")}
                    </Typography>
                    <Box sx={{ width: 28 }} />
                </Box>

                {/* Table rows */}
                {draft.map((m, i) => (
                    <Box
                        key={i}
                        sx={(theme) => ({
                            display: "flex",
                            alignItems: "center",
                            gap: 1,
                            px: 1,
                            py: 0.75,
                            "&:not(:last-child)": {
                                borderBottom: `1px solid ${theme.palette.divider}`,
                            },
                        })}
                    >
                        {/* VAR */}
                        <Box sx={{ flex: "0 0 48px", display: "flex", justifyContent: "center" }}>
                            <FormControl size="small" variant="outlined" sx={{ width: 40 }}>
                                <Select
                                    value={m.variable}
                                    onChange={(e) => handleChange(i, { variable: e.target.value })}
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
                                    {patternVariables.map((pv) => (
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

                        {/* FUNC */}
                        <Box sx={{ flex: "0 0 100px" }}>
                            <ToggleButtonGroup
                                value={m.func}
                                exclusive
                                size="small"
                                onChange={(_, val) => {
                                    if (val !== null) handleChange(i, { func: val });
                                }}
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
                        <Autocomplete
                            freeSolo
                            options={exprSuggestions}
                            value={m.expression}
                            onInputChange={(_, value) => handleChange(i, { expression: value })}
                            sx={{ flex: 1 }}
                            renderInput={(params) => (
                                <TextField
                                    {...params}
                                    size="small"
                                    placeholder={`${m.variable}.field`}
                                    sx={{ "& .MuiInputBase-input": { fontSize: "0.8rem", py: 0.75 } }}
                                />
                            )}
                        />

                        {/* ALIAS */}
                        <TextField
                            size="small"
                            value={m.alias}
                            onChange={(e) => handleChange(i, { alias: e.target.value })}
                            placeholder={t("flinkSql.cep.aliasPlaceholder", "alias")}
                            sx={{ flex: 1, "& .MuiInputBase-input": { fontSize: "0.8rem", py: 0.75 } }}
                        />

                        {/* DELETE */}
                        <IconButton size="small" onClick={() => handleDelete(i)} sx={{ p: 0.5 }}>
                            <CloseIcon sx={{ fontSize: "0.9rem" }} />
                        </IconButton>
                    </Box>
                ))}

                {/* Add button */}
                <Box sx={{ px: 1, py: 1 }}>
                    <Button
                        size="small"
                        startIcon={<AddIcon sx={{ fontSize: "0.85rem !important" }} />}
                        onClick={handleAdd}
                        sx={{ textTransform: "none", fontSize: "0.72rem", color: "text.secondary" }}
                    >
                        {t("flinkSql.cep.addMeasure", "Add measure")}
                    </Button>
                </Box>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} size="small" sx={{ textTransform: "none" }}>
                    {t("flinkSql.cep.measuresCancel", "Cancel")}
                </Button>
                <Button onClick={() => onApply(draft)} variant="contained" size="small" sx={{ textTransform: "none" }}>
                    {t("flinkSql.cep.measuresApply", "Apply")}
                </Button>
            </DialogActions>
        </Dialog>
    );
}
