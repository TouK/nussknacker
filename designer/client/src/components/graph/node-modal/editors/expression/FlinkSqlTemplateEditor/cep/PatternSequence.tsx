import AddIcon from "@mui/icons-material/Add";
import { Box, MenuItem, Popover, Select, TextField, ToggleButton, ToggleButtonGroup, Typography } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { InfoTooltip } from "../../../InfoTooltip/InfoTooltip";
import type { PatternVariable } from "../types";
import { getPatternVariableColor } from "../types";

interface Props {
    pattern: PatternVariable[];
    onChange: (pattern: PatternVariable[]) => void;
    readOnly?: boolean;
}

// ── Quantifier helpers ──────────────────────────────────────────────────────

type BaseQuantifier = "" | "+" | "{2,}" | "?" | "*" | "{n}" | "{n,m}";

const QUANTIFIER_OPTIONS: { label: string; value: BaseQuantifier }[] = [
    { label: "exactly once", value: "" },
    { label: "one or more (+)", value: "+" },
    { label: "two or more ({2,})", value: "{2,}" },
    { label: "zero or one (?)", value: "?" },
    { label: "zero or more (*)", value: "*" },
    { label: "exactly n ({n})", value: "{n}" },
    { label: "between n and m ({n,m})", value: "{n,m}" },
];

// Reluctant toggle is disabled for these bases (Flink doesn't support their reluctant forms)
const RELUCTANT_DISABLED = new Set<BaseQuantifier>(["", "?", "{n}"]);

function decomposeQuantifier(q: string): { base: BaseQuantifier; reluctant: boolean; n: number; m: number } {
    if (!q) return { base: "", reluctant: false, n: 1, m: 2 };
    if (q === "?") return { base: "?", reluctant: false, n: 1, m: 2 };

    const reluctant = q.endsWith("?");
    const core = reluctant ? q.slice(0, -1) : q;

    if (core === "+") return { base: "+", reluctant, n: 1, m: 2 };
    if (core === "*") return { base: "*", reluctant, n: 1, m: 2 };
    if (core === "{2,}") return { base: "{2,}", reluctant, n: 2, m: 3 };

    const exactN = core.match(/^\{(\d+)\}$/);
    if (exactN) return { base: "{n}", reluctant: false, n: parseInt(exactN[1], 10), m: parseInt(exactN[1], 10) + 1 };

    const rangeNM = core.match(/^\{(\d+),(\d+)\}$/);
    if (rangeNM) return { base: "{n,m}", reluctant, n: parseInt(rangeNM[1], 10), m: parseInt(rangeNM[2], 10) };

    return { base: core as BaseQuantifier, reluctant: false, n: 1, m: 2 };
}

function composeQuantifier(base: BaseQuantifier, reluctant: boolean, n: number, m: number): string {
    const r = reluctant && !RELUCTANT_DISABLED.has(base);
    switch (base) {
        case "":
            return "";
        case "?":
            return "?";
        case "+":
            return r ? "+?" : "+";
        case "{2,}":
            return r ? "{2,}?" : "{2,}";
        case "*":
            return r ? "*?" : "*";
        case "{n}":
            return `{${n}}`;
        case "{n,m}":
            return r ? `{${n},${m}}?` : `{${n},${m}}`;
        default:
            return base;
    }
}

// ── Component ───────────────────────────────────────────────────────────────

const NEXT_NAME = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

export function PatternSequence({ pattern, onChange, readOnly }: Props) {
    const { t } = useTranslation();
    const [editAnchor, setEditAnchor] = useState<HTMLElement | null>(null);
    const [editIndex, setEditIndex] = useState<number | null>(null);

    const editingVar = editIndex !== null ? pattern[editIndex] : null;

    const handleAdd = () => {
        const used = new Set(pattern.map((p) => p.name));
        const next = NEXT_NAME.split("").find((c) => !used.has(c)) ?? "X";
        onChange([
            ...pattern,
            { name: next, description: "", quantifier: "", conditions: [{ mode: "simple", field: "", operator: "=", value: "" }] },
        ]);
    };

    const handleDelete = (i: number) => {
        onChange(pattern.filter((_, idx) => idx !== i));
    };

    const handleEditVar = (update: Partial<PatternVariable>) => {
        if (editIndex === null) return;
        onChange(pattern.map((p, i) => (i === editIndex ? { ...p, ...update } : p)));
    };

    const patternText = pattern.map((pv) => (pv.quantifier ? `${pv.name}${pv.quantifier}` : pv.name)).join(" ");

    return (
        <Box
            sx={(theme) => ({
                border: `1px solid ${theme.palette.divider}`,
                borderRadius: 1.5,
                overflow: "hidden",
            })}
        >
            {/* Pills row */}
            <Box sx={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 1, px: 1.5, py: 1.25 }}>
                {pattern.map((pv, i) => {
                    const color = getPatternVariableColor(pv.name);
                    return (
                        <React.Fragment key={i}>
                            {i > 0 && (
                                <Typography
                                    variant="caption"
                                    sx={{ color: "text.disabled", fontSize: "0.85rem", lineHeight: 1, userSelect: "none" }}
                                >
                                    {"\u2192"}
                                </Typography>
                            )}
                            <Box
                                onClick={
                                    readOnly
                                        ? undefined
                                        : (e) => {
                                              setEditIndex(i);
                                              setEditAnchor(e.currentTarget as HTMLElement);
                                          }
                                }
                                sx={{
                                    position: "relative",
                                    width: 32,
                                    height: 32,
                                    borderRadius: "50%",
                                    backgroundColor: color,
                                    display: "flex",
                                    alignItems: "center",
                                    justifyContent: "center",
                                    color: "#fff",
                                    fontWeight: 700,
                                    fontSize: "0.85rem",
                                    cursor: readOnly ? "default" : "pointer",
                                    transition: "box-shadow 0.15s",
                                    "&:hover": readOnly ? {} : { boxShadow: `0 0 0 3px ${color}44` },
                                }}
                            >
                                {pv.name}
                                {pv.quantifier && (
                                    <Box
                                        sx={{
                                            position: "absolute",
                                            top: -4,
                                            right: -8,
                                            px: 0.4,
                                            py: 0.05,
                                            borderRadius: 0.5,
                                            backgroundColor: "rgba(0,0,0,0.7)",
                                            color: "#fff",
                                            fontSize: "0.55rem",
                                            fontWeight: 600,
                                            lineHeight: 1.3,
                                            whiteSpace: "nowrap",
                                        }}
                                    >
                                        {pv.quantifier}
                                    </Box>
                                )}
                            </Box>
                        </React.Fragment>
                    );
                })}
                {!readOnly && (
                    <Box
                        onClick={handleAdd}
                        sx={{
                            width: 32,
                            height: 32,
                            borderRadius: "50%",
                            border: "2px dashed rgba(255,255,255,0.25)",
                            display: "flex",
                            alignItems: "center",
                            justifyContent: "center",
                            cursor: "pointer",
                            transition: "border-color 0.15s",
                            "&:hover": { borderColor: "rgba(255,255,255,0.5)" },
                        }}
                    >
                        <AddIcon sx={{ fontSize: "1rem", color: "text.disabled" }} />
                    </Box>
                )}
            </Box>

            {/* Pattern text preview */}
            <Box
                sx={(theme) => ({ px: 1.5, py: 0.75, borderTop: `1px solid ${theme.palette.divider}`, backgroundColor: "rgba(0,0,0,0.1)" })}
            >
                <Typography
                    variant="caption"
                    sx={{ fontFamily: "'JetBrains Mono', 'Fira Code', monospace", fontSize: "0.7rem", color: "text.disabled" }}
                >
                    PATTERN ( {patternText} )
                </Typography>
            </Box>

            {/* Edit popover */}
            <Popover
                open={editAnchor !== null}
                anchorEl={editAnchor}
                onClose={() => {
                    setEditAnchor(null);
                    setEditIndex(null);
                }}
                anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
                slotProps={{ paper: { sx: (theme) => ({ mt: 0.5, border: `1px solid ${theme.palette.divider}`, borderRadius: 1.5 }) } }}
            >
                {editingVar &&
                    (() => {
                        const { base, reluctant, n, m } = decomposeQuantifier(editingVar.quantifier ?? "");

                        const applyQuantifier = (newBase?: BaseQuantifier, newReluctant?: boolean, newN?: number, newM?: number) => {
                            handleEditVar({
                                quantifier: composeQuantifier(newBase ?? base, newReluctant ?? reluctant, newN ?? n, newM ?? m),
                            });
                        };

                        return (
                            <Box p={2} display="flex" flexDirection="column" gap={1.5} sx={{ minWidth: 300 }}>
                                <TextField
                                    label={t("flinkSql.cep.patternVarName", "Variable name")}
                                    size="small"
                                    value={editingVar.name}
                                    onChange={(e) => handleEditVar({ name: e.target.value.toUpperCase().slice(0, 1) || editingVar.name })}
                                    inputProps={{ maxLength: 1 }}
                                />
                                <TextField
                                    label={t("flinkSql.cep.patternVarDesc", "Description (optional)")}
                                    size="small"
                                    value={editingVar.description ?? ""}
                                    onChange={(e) => handleEditVar({ description: e.target.value })}
                                />

                                {/* Quantifier row: dropdown + greedy/reluctant */}
                                <Box display="flex" gap={1} alignItems="center">
                                    <Select
                                        size="small"
                                        value={base}
                                        onChange={(e) => applyQuantifier(e.target.value as BaseQuantifier)}
                                        sx={{ flex: 1, fontSize: "0.8rem" }}
                                    >
                                        {QUANTIFIER_OPTIONS.map((q) => (
                                            <MenuItem key={q.value} value={q.value} sx={{ fontSize: "0.8rem" }}>
                                                {q.label}
                                            </MenuItem>
                                        ))}
                                    </Select>
                                    <Box display="flex" alignItems="center" gap={0.5}>
                                        <ToggleButtonGroup
                                            size="small"
                                            exclusive
                                            value={reluctant && !RELUCTANT_DISABLED.has(base) ? "reluctant" : "greedy"}
                                            onChange={(_, val) => {
                                                if (val) applyQuantifier(undefined, val === "reluctant");
                                            }}
                                            disabled={RELUCTANT_DISABLED.has(base)}
                                            sx={{
                                                height: 32,
                                                "& .MuiToggleButton-root": { fontSize: "0.65rem", px: 0.75, py: 0, textTransform: "none" },
                                            }}
                                        >
                                            <ToggleButton value="greedy" disableRipple>
                                                greedy
                                            </ToggleButton>
                                            <ToggleButton value="reluctant" disableRipple>
                                                reluctant
                                            </ToggleButton>
                                        </ToggleButtonGroup>
                                        <InfoTooltip
                                            title={
                                                RELUCTANT_DISABLED.has(base)
                                                    ? "Reluctant mode is not applicable for this quantifier."
                                                    : "Greedy (default): matches as many events as possible. Reluctant: matches as few events as possible — stops at the first valid completion of the pattern."
                                            }
                                            variant="hover"
                                            placement="top"
                                        />
                                    </Box>
                                </Box>

                                {/* n / m inputs for {n} and {n,m} */}
                                {(base === "{n}" || base === "{n,m}") && (
                                    <Box display="flex" gap={1}>
                                        <TextField
                                            label="n"
                                            size="small"
                                            type="number"
                                            value={n}
                                            onChange={(e) =>
                                                applyQuantifier(undefined, undefined, Math.max(1, parseInt(e.target.value, 10) || 1))
                                            }
                                            inputProps={{ min: 1 }}
                                            sx={{ flex: 1 }}
                                        />
                                        {base === "{n,m}" && (
                                            <TextField
                                                label="m"
                                                size="small"
                                                type="number"
                                                value={m}
                                                onChange={(e) =>
                                                    applyQuantifier(
                                                        undefined,
                                                        undefined,
                                                        undefined,
                                                        Math.max(1, parseInt(e.target.value, 10) || 1),
                                                    )
                                                }
                                                inputProps={{ min: 1 }}
                                                sx={{ flex: 1 }}
                                            />
                                        )}
                                    </Box>
                                )}

                                {pattern.length > 1 && (
                                    <Typography
                                        variant="caption"
                                        color="error"
                                        sx={{ cursor: "pointer", alignSelf: "flex-end", "&:hover": { textDecoration: "underline" } }}
                                        onClick={() => {
                                            handleDelete(editIndex!);
                                            setEditAnchor(null);
                                            setEditIndex(null);
                                        }}
                                    >
                                        {t("flinkSql.cep.removeVariable", "Remove variable")}
                                    </Typography>
                                )}
                            </Box>
                        );
                    })()}
            </Popover>
        </Box>
    );
}
