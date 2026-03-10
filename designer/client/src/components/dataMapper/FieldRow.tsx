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
import React, { useCallback, useEffect, useRef } from "react";

import type { VariableTypes } from "../../types/validation";
import { clearAceSelectionAfterDrop } from "../builderComponents/aceUtils";
import { SpelEditorContainer } from "../builderComponents/panelStyles";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import type { FieldError } from "../graph/node-modal/editors/Validators";
import type { FieldDef, NuType } from "./dataMapperUtils";
import { NU_TYPES } from "./dataMapperUtils";

// ─── Styled ───────────────────────────────────────────────────────────────────

const TypeChip = styled(Chip)<{ nutype: NuType | "Record" }>(({ theme, nutype }) => {
    const typeColors: Record<NuType | "Record", string> = {
        String: "#E8B4B8",
        Integer: "#B4C7E8",
        Long: "#C7B4E8",
        Float: "#B4E8D4",
        Double: "#B4E8D4",
        Boolean: "#E8D4B4",
        BigDecimal: "#E8E0B4",
        List: "#D4B4E8",
        Map: "#B4E8E8",
        Record: "#93C5FD",
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

export interface FieldRowProps {
    field: FieldDef;
    depth: number;
    selFieldId: number | null;
    dragOverFieldId: number | null;
    variableTypes: VariableTypes;
    fieldErrors: Record<number, FieldError[]>;
    onSelectId: (id: number | null) => void;
    onDragOverId: (id: number | null) => void;
    onChange: (id: number, key: keyof FieldDef, val: unknown) => void;
    onAddChild: (parentId: number) => void;
    onRemove: (id: number) => void;
    onMove: (id: number, dir: 1 | -1) => void;
    onDrop: (path: string, fieldId: number) => void;
    onValidateExpression: (id: number, expression: string, nuType: NuType) => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function FieldRow({
    field,
    depth,
    selFieldId,
    dragOverFieldId,
    variableTypes,
    fieldErrors,
    onSelectId,
    onDragOverId,
    onChange,
    onAddChild,
    onRemove,
    onMove,
    onDrop,
    onValidateExpression,
}: FieldRowProps): React.JSX.Element {
    const theme = useTheme();
    const isSelected = selFieldId === field.id;
    const isDragOver = dragOverFieldId === field.id;
    const hasSrc = !field.isRecord && !!field.expression;
    const errors = fieldErrors[field.id] ?? [];

    const onExpressionChangeRef = useRef<(val: string) => void>(null!);
    const onExpressionChange = useCallback((val: string) => onChange(field.id, "expression", val), [field.id, onChange]);
    onExpressionChangeRef.current = onExpressionChange;
    const stableExpressionChange = useCallback((val: string) => onExpressionChangeRef.current(val), []);

    const onValidateRef = useRef(onValidateExpression);
    onValidateRef.current = onValidateExpression;
    useEffect(() => {
        if (field.isRecord) return;
        const timer = setTimeout(() => onValidateRef.current(field.id, field.expression, field.type), 500);
        return () => clearTimeout(timer);
    }, [field.id, field.isRecord, field.expression, field.type]);

    const typeLabel: NuType | "Record" = field.isRecord ? "Record" : field.type === "Map" ? "Any" : field.type;

    return (
        // Outer box: only border + drag handlers. NO background — prevents stacking through nesting.
        <Box
            onDragOver={(e) => {
                e.preventDefault();
                e.stopPropagation();
                onDragOverId(field.id);
            }}
            onDragLeave={() => onDragOverId(null)}
            onDrop={(e) => {
                e.preventDefault();
                e.stopPropagation();
                const path = e.dataTransfer.getData("text/plain");
                if (path) onDrop(path, field.id);
            }}
            sx={{
                mb: "2px",
                borderRadius: 1,
                border: `1px solid ${isDragOver ? theme.palette.primary.main : isSelected ? theme.palette.divider : "transparent"}`,
            }}
        >
            {/* Summary row: click + background live here only */}
            <Box
                onClick={() => onSelectId(isSelected ? null : field.id)}
                sx={{
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                    px: 1.5,
                    py: 1,
                    borderRadius: 1,
                    cursor: "pointer",
                    transition: "background-color 0.15s",
                    backgroundColor: isDragOver
                        ? alpha(theme.palette.primary.main, 0.08)
                        : isSelected
                        ? alpha(theme.palette.primary.main, 0.08)
                        : "transparent",
                    "&:hover": {
                        backgroundColor: isSelected ? alpha(theme.palette.primary.main, 0.1) : alpha(theme.palette.action.hover, 0.4),
                    },
                }}
            >
                <Tooltip
                    title={field.isRecord ? "Record field" : hasSrc ? "Mapped" : "Not yet mapped — drag a field or type a value"}
                    placement="left"
                >
                    <Box sx={{ display: "flex", alignItems: "center", flexShrink: 0 }}>
                        {field.isRecord ? (
                            <Box sx={{ width: 8, height: 8, borderRadius: 1, backgroundColor: "#93C5FD" }} />
                        ) : hasSrc ? (
                            <Box sx={{ width: 8, height: 8, borderRadius: "50%", backgroundColor: theme.palette.success.main }} />
                        ) : (
                            <WarningAmberIcon sx={{ fontSize: 14, color: "warning.main", opacity: 0.8 }} />
                        )}
                    </Box>
                </Tooltip>
                <Typography sx={{ fontSize: 13, fontWeight: 500, minWidth: 100 }}>
                    {field.name || <span style={{ color: theme.palette.text.disabled }}>(unnamed)</span>}
                </Typography>
                <TypeChip label={typeLabel} nutype={typeLabel} size="small" />
                {field.isRecord && (
                    <Chip
                        label={`{${field.children.length} field${field.children.length !== 1 ? "s" : ""}}`}
                        size="small"
                        sx={{
                            height: 18,
                            fontSize: 10,
                            fontWeight: 500,
                            color: "#93C5FD",
                            backgroundColor: alpha("#93C5FD", 0.15),
                            border: `1px solid ${alpha("#93C5FD", 0.4)}`,
                            "& .MuiChip-label": { px: "6px" },
                        }}
                    />
                )}
                {!field.isRecord && field.expression && (
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
                            onMove(field.id, -1);
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
                            onMove(field.id, 1);
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
                            onRemove(field.id);
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
                    sx={{
                        mx: 1.5,
                        mb: 1,
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
                            onChange={(e) => onChange(field.id, "name", e.target.value)}
                            size="small"
                            variant="outlined"
                            sx={{ flex: 1, "& .MuiInputBase-input": { fontSize: 12, fontFamily: "monospace", py: "5px" } }}
                        />
                    </Box>

                    {/* Type */}
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 1 }}>
                        <Typography sx={{ fontSize: 11, color: "text.secondary", width: 70 }}>Type</Typography>
                        {field.isRecord ? (
                            <>
                                <Typography sx={{ fontSize: 12, color: "text.secondary", flex: 1 }}>Nested record</Typography>
                                <Button
                                    size="small"
                                    variant="outlined"
                                    color="inherit"
                                    onClick={() => onChange(field.id, "isRecord", false)}
                                    sx={{ fontSize: 11, py: "2px", textTransform: "none", flexShrink: 0 }}
                                >
                                    Switch to expression
                                </Button>
                            </>
                        ) : (
                            <>
                                <Select
                                    value={field.type === "Map" ? "Any" : field.type}
                                    onChange={(e) => onChange(field.id, "type", e.target.value as NuType)}
                                    size="small"
                                    sx={{ flex: 1, fontSize: 12, "& .MuiSelect-select": { py: "5px" } }}
                                >
                                    {NU_TYPES.filter((t) => t !== "Map").map((t) => (
                                        <MenuItem key={t} value={t} sx={{ fontSize: 12 }}>
                                            {t}
                                        </MenuItem>
                                    ))}
                                </Select>
                                <Button
                                    size="small"
                                    variant="outlined"
                                    color="inherit"
                                    onClick={() => onChange(field.id, "isRecord", true)}
                                    sx={{ fontSize: 11, py: "2px", textTransform: "none", flexShrink: 0 }}
                                >
                                    Build Record
                                </Button>
                            </>
                        )}
                    </Box>

                    {/* SpEL expression (leaf only) */}
                    {!field.isRecord && (
                        <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1.5, mt: 1 }}>
                            <Typography sx={{ fontSize: 11, color: "text.secondary", width: 70, pt: "6px" }}>value</Typography>
                            <Box sx={{ flex: 1 }}>
                                <SpelEditorContainer onDrop={(e) => clearAceSelectionAfterDrop(e.currentTarget)}>
                                    <ExpressionSuggest
                                        inputProps={{
                                            value: field.expression,
                                            language: ExpressionLang.SpEL,
                                            onValueChange: stableExpressionChange,
                                            rows: 1,
                                            placeholder: "#input.field or #MATH.abs(#input.value)",
                                        }}
                                        showValidation
                                        variableTypes={variableTypes}
                                        fieldErrors={errors}
                                    />
                                </SpelEditorContainer>
                            </Box>
                        </Box>
                    )}
                </Box>
            </Collapse>

            {/* Nested children — sibling of the summary row, so background never stacks */}
            {field.isRecord && (
                <Box
                    sx={{
                        mt: 0.5,
                        ml: 1,
                        pl: 1.5,
                        pb: 0.5,
                        borderLeft: `2px solid ${alpha(theme.palette.primary.main, 0.25)}`,
                    }}
                >
                    {field.children.length === 0 && (
                        <Typography sx={{ fontSize: 11, color: "text.disabled", py: 0.5 }}>No fields yet — click Add field</Typography>
                    )}
                    {field.children.map((child) => (
                        <FieldRow
                            key={child.id}
                            field={child}
                            depth={depth + 1}
                            selFieldId={selFieldId}
                            dragOverFieldId={dragOverFieldId}
                            variableTypes={variableTypes}
                            fieldErrors={fieldErrors}
                            onSelectId={onSelectId}
                            onDragOverId={onDragOverId}
                            onChange={onChange}
                            onAddChild={onAddChild}
                            onRemove={onRemove}
                            onMove={onMove}
                            onDrop={onDrop}
                            onValidateExpression={onValidateExpression}
                        />
                    ))}
                    <Button
                        size="small"
                        startIcon={<AddIcon />}
                        onClick={() => onAddChild(field.id)}
                        sx={{ fontSize: 11, textTransform: "none", mt: 0.5, py: "2px" }}
                    >
                        Add field
                    </Button>
                </Box>
            )}
        </Box>
    );
}
