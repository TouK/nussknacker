import AddIcon from "@mui/icons-material/Add";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import DeleteIcon from "@mui/icons-material/Delete";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import {
    alpha,
    Box,
    Button,
    Chip,
    Collapse,
    IconButton,
    MenuItem,
    Select,
    styled,
    TextField,
    Tooltip,
    Typography,
    useTheme,
} from "@mui/material";
import React, { useCallback, useRef } from "react";

import type { VariableTypes } from "../../types/validation";
import { clearAceSelectionAfterDrop } from "../builderComponents/aceUtils";
import { SpelEditorContainer } from "../builderComponents/panelStyles";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import type { FieldDef, MapEntryDef, NuType } from "./dataMapperUtils";
import { NU_TYPES, nextId } from "./dataMapperUtils";
import { MapEntryRow } from "./MapEntryRow";

// ─── Styled ───────────────────────────────────────────────────────────────────

const TypeChip = styled(Chip)<{ nutype: NuType }>(({ theme, nutype }) => {
    const typeColors: Record<NuType, string> = {
        String: "#E8B4B8",
        Integer: "#B4C7E8",
        Long: "#C7B4E8",
        Float: "#B4E8D4",
        Double: "#B4E8D4",
        Boolean: "#E8D4B4",
        BigDecimal: "#E8E0B4",
        List: "#D4B4E8",
        Map: "#B4E8E8",
        Any: theme.palette.text.disabled,
    };
    return {
        height: 18,
        fontSize: 10,
        fontWeight: 600,
        color: "#000",
        backgroundColor: typeColors[nutype] ?? theme.palette.text.disabled,
        "& .MuiChip-label": { padding: "0 6px" },
    };
});

// ─── Props ────────────────────────────────────────────────────────────────────

interface FieldRowProps {
    field: FieldDef;
    isSelected: boolean;
    isDragOver: boolean;
    variableTypes: VariableTypes;
    onSelect: () => void;
    onChange: (key: keyof FieldDef, value: unknown) => void;
    onMapEntryAdd: () => void;
    onRemove: () => void;
    onMoveUp: () => void;
    onMoveDown: () => void;
    onDragOver: () => void;
    onDragLeave: () => void;
    onDrop: (path: string) => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function FieldRow({
    field,
    isSelected,
    isDragOver,
    variableTypes,
    onSelect,
    onChange,
    onMapEntryAdd,
    onRemove,
    onMoveUp,
    onMoveDown,
    onDragOver,
    onDragLeave,
    onDrop,
}: FieldRowProps): React.JSX.Element {
    const theme = useTheme();
    const hasSrc = field.expression || (field.useMapBuilder && field.mapEntries.length > 0);

    const onChangeRef = useRef(onChange);
    onChangeRef.current = onChange;
    const onExpressionChange = useCallback((val: string) => onChangeRef.current("expression", val), []);

    const updateMapEntry = (entryId: number, key: keyof MapEntryDef, val: unknown) => {
        onChange(
            "mapEntries",
            field.mapEntries.map((e) => (e.id !== entryId ? e : { ...e, [key]: val })),
        );
    };
    const removeMapEntry = (entryId: number) => {
        onChange(
            "mapEntries",
            field.mapEntries.filter((e) => e.id !== entryId),
        );
    };

    return (
        <Box
            onDragOver={(e) => {
                e.preventDefault();
                onDragOver();
            }}
            onDragLeave={onDragLeave}
            onDrop={(e) => {
                e.preventDefault();
                const path = e.dataTransfer.getData("text/plain");
                if (path) onDrop(path);
            }}
            onClick={onSelect}
            sx={{
                px: 1.5,
                py: 1,
                mb: "2px",
                borderRadius: 1,
                border: `1px solid ${isDragOver ? theme.palette.primary.main : isSelected ? theme.palette.divider : "transparent"}`,
                backgroundColor: isDragOver
                    ? alpha(theme.palette.primary.main, 0.08)
                    : isSelected
                    ? alpha(theme.palette.background.paper, 0.8)
                    : "transparent",
                cursor: "pointer",
                transition: "all 0.15s",
                "&:hover": { backgroundColor: isSelected ? undefined : alpha(theme.palette.action.hover, 0.4) },
            }}
        >
            {/* Summary row */}
            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <Tooltip title={hasSrc ? "Mapped" : "Not yet mapped — drag a field or type a value"} placement="left">
                    <Box sx={{ display: "flex", alignItems: "center", flexShrink: 0 }}>
                        {hasSrc ? (
                            <Box sx={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: theme.palette.success.main }} />
                        ) : (
                            <WarningAmberIcon sx={{ fontSize: 14, color: "warning.main", opacity: 0.8 }} />
                        )}
                    </Box>
                </Tooltip>
                <Typography sx={{ fontSize: 13, fontWeight: 500, minWidth: 100 }}>
                    {field.name || <span style={{ color: theme.palette.text.disabled }}>(unnamed)</span>}
                </Typography>
                <TypeChip label={field.type} nutype={field.type} size="small" />
                {field.useMapBuilder && field.mapEntries.length > 0 && (
                    <Chip
                        label={`{${field.mapEntries.length} entr${field.mapEntries.length === 1 ? "y" : "ies"}}`}
                        size="small"
                        sx={{
                            height: 18,
                            fontSize: 10,
                            color: theme.palette.secondary.light,
                            backgroundColor: alpha(theme.palette.secondary.main, 0.15),
                            "& .MuiChip-label": { px: "6px" },
                        }}
                    />
                )}
                {!field.useMapBuilder && field.expression && (
                    <Chip
                        label={field.expression.length > 30 ? field.expression.slice(0, 30) + "…" : field.expression}
                        size="small"
                        sx={{
                            height: 18,
                            fontSize: 10,
                            fontFamily: "monospace",
                            color: theme.palette.primary.light,
                            backgroundColor: alpha(theme.palette.primary.main, 0.12),
                            "& .MuiChip-label": { px: "6px" },
                        }}
                    />
                )}
                <Box sx={{ flex: 1 }} />
                <Tooltip title="Move up">
                    <IconButton
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            onMoveUp();
                        }}
                        sx={{ p: "2px", color: "text.disabled" }}
                    >
                        <ArrowUpwardIcon sx={{ fontSize: 14 }} />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Move down">
                    <IconButton
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            onMoveDown();
                        }}
                        sx={{ p: "2px", color: "text.disabled" }}
                    >
                        <ArrowDownwardIcon sx={{ fontSize: 14 }} />
                    </IconButton>
                </Tooltip>
                <Tooltip title="Remove field">
                    <IconButton
                        size="small"
                        onClick={(e) => {
                            e.stopPropagation();
                            onRemove();
                        }}
                        sx={{ p: "2px", color: "text.disabled", "&:hover": { color: "error.main" } }}
                    >
                        <DeleteIcon sx={{ fontSize: 14 }} />
                    </IconButton>
                </Tooltip>
            </Box>

            {/* Expanded editor */}
            <Collapse in={isSelected}>
                <Box
                    onClick={(e) => e.stopPropagation()}
                    sx={{
                        mt: 1,
                        p: 1.5,
                        backgroundColor: theme.palette.background.default,
                        borderRadius: 1,
                        border: `1px solid ${theme.palette.divider}`,
                    }}
                >
                    {/* Name */}
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 1 }}>
                        <Typography sx={{ fontSize: 11, color: "text.secondary", width: 70 }}>Name</Typography>
                        <TextField
                            value={field.name}
                            onChange={(e) => onChange("name", e.target.value)}
                            size="small"
                            variant="outlined"
                            sx={{ flex: 1, "& .MuiInputBase-input": { fontSize: 12, fontFamily: "monospace", py: "5px" } }}
                        />
                    </Box>

                    {/* Type (only relevant for Build Map toggle) */}
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 1 }}>
                        <Typography sx={{ fontSize: 11, color: "text.secondary", width: 70 }}>Type</Typography>
                        <Select
                            value={field.type}
                            onChange={(e) => onChange("type", e.target.value as NuType)}
                            size="small"
                            sx={{ flex: 1, fontSize: 12, "& .MuiSelect-select": { py: "5px" } }}
                        >
                            {NU_TYPES.map((t) => (
                                <MenuItem key={t} value={t} sx={{ fontSize: 12 }}>
                                    {t}
                                </MenuItem>
                            ))}
                        </Select>
                        {field.type === "Map" && (
                            <Button
                                size="small"
                                variant={field.useMapBuilder ? "contained" : "outlined"}
                                color={field.useMapBuilder ? "secondary" : "inherit"}
                                onClick={() => onChange("useMapBuilder", !field.useMapBuilder)}
                                sx={{ fontSize: 11, py: "2px", textTransform: "none", flexShrink: 0 }}
                            >
                                Build Map
                            </Button>
                        )}
                    </Box>

                    {/* Build Map entries */}
                    {field.useMapBuilder ? (
                        <Box
                            sx={{ border: `1px solid ${theme.palette.divider}`, borderRadius: 1, p: 1, mb: 1 }}
                            onDragOver={(e) => e.preventDefault()}
                            onDrop={(e) => {
                                e.preventDefault();
                                const path = e.dataTransfer.getData("text/plain");
                                if (path) {
                                    onChange("mapEntries", [
                                        ...field.mapEntries,
                                        {
                                            id: nextId(),
                                            key: path.replace(/^#?/, "").replace(/\?\./g, ".").split(".").pop() ?? "",
                                            expression: path,
                                        },
                                    ]);
                                }
                            }}
                        >
                            {field.mapEntries.length === 0 && (
                                <Typography sx={{ fontSize: 11, color: "text.disabled", mb: 0.5 }}>
                                    Drop a field here or click Add entry
                                </Typography>
                            )}
                            {field.mapEntries.map((entry) => (
                                <MapEntryRow
                                    key={entry.id}
                                    entry={entry}
                                    variableTypes={variableTypes}
                                    onChange={(k, v) => updateMapEntry(entry.id, k, v)}
                                    onRemove={() => removeMapEntry(entry.id)}
                                />
                            ))}
                            <Button
                                size="small"
                                startIcon={<AddIcon />}
                                onClick={onMapEntryAdd}
                                sx={{ fontSize: 11, textTransform: "none", mt: 0.5, py: "2px" }}
                            >
                                Add entry
                            </Button>
                        </Box>
                    ) : (
                        /* SpEL expression */
                        <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1.5 }}>
                            <Typography sx={{ fontSize: 11, color: "text.secondary", width: 70, pt: "6px" }}>value</Typography>
                            <SpelEditorContainer onDrop={(e) => clearAceSelectionAfterDrop(e.currentTarget)}>
                                <ExpressionSuggest
                                    inputProps={{
                                        value: field.expression,
                                        language: ExpressionLang.SpEL,
                                        onValueChange: onExpressionChange,
                                        rows: 1,
                                        placeholder: "#input.field or #MATH.abs(#input.value)",
                                    }}
                                    variableTypes={variableTypes}
                                    fieldErrors={[]}
                                />
                            </SpelEditorContainer>
                        </Box>
                    )}
                </Box>
            </Collapse>
        </Box>
    );
}
