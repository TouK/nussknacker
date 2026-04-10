import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import { Autocomplete, Box, Button, Divider, IconButton, TextField, ToggleButton, ToggleButtonGroup, Typography } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { LoadingButtonTypes } from "../../../../../../../windowManager/LoadingButton";
import { WindowContent } from "../../../../../../../windowManager/WindowContent";
import type { Measure, PatternVariable } from "../types";

export interface EditCepMeasuresData {
    measures: Measure[];
    patternVariables: PatternVariable[];
    fields: string[];
    outputVar?: string;
    onApply: (measures: Measure[]) => void;
}

export function MeasuresDialogContent(props: WindowContentProps) {
    const { t } = useTranslation();
    const { measures, patternVariables, fields, outputVar, onApply } = props.data.meta as EditCepMeasuresData;

    const [draft, setDraft] = useState<Measure[]>([]);

    useEffect(() => {
        setDraft(measures.map((m) => ({ ...m })));
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    const exprSuggestions = useMemo(
        () => patternVariables.flatMap((pv) => fields.map((f) => `${pv.name}.${f}`)),
        [patternVariables, fields],
    );

    const handleChange = (i: number, update: Partial<Measure>) => {
        setDraft((prev) => prev.map((m, idx) => (idx === i ? { ...m, ...update } : m)));
    };

    const handleAdd = () => {
        setDraft((prev) => [...prev, { variable: patternVariables[0]?.name ?? "", func: "", expression: "", alias: "" }]);
    };

    const handleDelete = (i: number) => {
        setDraft((prev) => prev.filter((_, idx) => idx !== i));
    };

    const buttons = useMemo(
        () => [
            { title: t("dialog.button.cancel", "Cancel"), action: () => props.close(), classname: LoadingButtonTypes.secondaryButton },
            {
                title: t("dialog.button.apply", "Apply"),
                action: () => {
                    onApply(draft);
                    props.close();
                },
            },
        ],
        [draft, onApply, props, t],
    );

    return (
        <WindowContent {...props} buttons={buttons} closeWithEsc>
            <Box sx={{ minWidth: 480 }}>
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
                    {(
                        [
                            { label: t("flinkSql.cep.measuresFunc", "Func"), flex: "0 0 100px" },
                            { label: t("flinkSql.cep.measuresExpr", "Expression"), flex: 1 },
                            { label: t("flinkSql.cep.measuresField", "Field"), flex: 1 },
                        ] as const
                    ).map(({ label, flex }, colIdx) => (
                        <Box key={label} sx={{ flex, display: "flex", alignItems: "center", gap: 0.5 }}>
                            <Typography
                                variant="caption"
                                sx={{
                                    textTransform: "uppercase",
                                    letterSpacing: "0.08em",
                                    fontWeight: 600,
                                    fontSize: "0.6rem",
                                    color: "text.secondary",
                                }}
                            >
                                {label}
                            </Typography>
                            {colIdx === 2 && outputVar && (
                                <Typography
                                    variant="caption"
                                    sx={{ fontSize: "0.6rem", color: "text.disabled", textTransform: "none", letterSpacing: 0 }}
                                >
                                    → {`#${outputVar}`}.<em>field</em>
                                </Typography>
                            )}
                        </Box>
                    ))}
                    <Box sx={{ width: 28 }} />
                </Box>

                {/* Rows */}
                {draft.map((m, i) => (
                    <Box
                        key={i}
                        sx={(theme) => ({
                            display: "flex",
                            alignItems: "center",
                            gap: 1,
                            px: 1,
                            py: 0.75,
                            "&:not(:last-child)": { borderBottom: `1px solid ${theme.palette.divider}` },
                        })}
                    >
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
                                    —
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
                                    placeholder="A.field"
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

                <Divider />
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
            </Box>
        </WindowContent>
    );
}
