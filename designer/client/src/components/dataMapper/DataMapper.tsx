import AddIcon from "@mui/icons-material/Add";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import DeleteIcon from "@mui/icons-material/Delete";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import SearchIcon from "@mui/icons-material/Search";
import UploadFileIcon from "@mui/icons-material/UploadFile";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import {
    alpha,
    Box,
    Button,
    Chip,
    Collapse,
    IconButton,
    InputAdornment,
    MenuItem,
    Paper,
    Select,
    styled,
    TextField,
    Tooltip,
    Typography,
    useTheme,
} from "@mui/material";
import React, { useCallback, useMemo, useRef, useState } from "react";

import HttpService from "../../http/HttpService/instance";
import { useAppSelector } from "../../store/storeHelpers";
import type { VariableTypes } from "../../types/validation";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import { rowAceEditor, nodeValue } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";
import { getProcessName, getProcessProperties } from "../graph/node-modal/NodeDetailsContent/selectors";

// ─── Types ────────────────────────────────────────────────────────────────────

type NuType = "String" | "Integer" | "Long" | "Float" | "Double" | "Boolean" | "BigDecimal" | "List" | "Map" | "Any";

interface MapEntryDef {
    id: number;
    key: string;
    expression: string;
}

interface FieldDefinition {
    id: number;
    name: string;
    type: NuType;
    expression: string;
    mapEntries: MapEntryDef[];
    useMapBuilder: boolean;
}

type ContextData = Record<string, unknown>;

// ─── Constants ────────────────────────────────────────────────────────────────

const NU_TYPES: NuType[] = ["String", "Integer", "Long", "Float", "Double", "Boolean", "BigDecimal", "List", "Map", "Any"];

const SAMPLE_CONTEXT: ContextData = {
    input: {},
    http_output: {
        request: {
            headers: [
                { name: "Accept-Encoding", value: "gzip, deflate" },
                { name: "Content-Type", value: "application/json; charset=utf-8" },
            ],
            method: "GET",
            body: {},
            url: "https://opensky-network.org/api/states/all",
        },
        response: {
            headers: [],
            body: {
                time: 1772630871,
                states: [
                    [
                        "3c6447",
                        "",
                        "Germany",
                        1772630844,
                        1772630844,
                        20.9692,
                        52.1707,
                        null,
                        true,
                        3.34,
                        151.88,
                        null,
                        null,
                        null,
                        null,
                        false,
                        0,
                    ],
                    [
                        "48af09",
                        "LOT672  ",
                        "Poland",
                        1772630870,
                        1772630870,
                        20.9974,
                        52.1311,
                        198.12,
                        false,
                        69.3,
                        331.15,
                        -3.9,
                        null,
                        320.04,
                        "6563",
                        false,
                        0,
                    ],
                ],
            },
            statusCode: 200,
            statusText: "",
        },
    },
};

// ─── Styled components ────────────────────────────────────────────────────────

const RootBox = styled(Box, { shouldForwardProp: (p) => p !== "embedded" })<{ embedded?: boolean }>(({ theme, embedded }) => ({
    display: "flex",
    flexDirection: "column",
    gap: 0,
    padding: 0,
    ...(embedded
        ? { flex: 1, minHeight: 0, overflow: "hidden", height: "100%" }
        : { minHeight: "100vh", gap: theme.spacing(2), padding: theme.spacing(2.5) }),
    backgroundColor: theme.palette.background.default,
    fontFamily: theme.typography.fontFamily,
}));

const PanelPaper = styled(Paper)(({ theme }) => ({
    border: `1px solid ${theme.palette.divider}`,
    borderRadius: theme.shape.borderRadius,
    overflow: "hidden",
    display: "flex",
    flexDirection: "column",
}));

const PanelHeader = styled(Box)(({ theme }) => ({
    padding: theme.spacing(1.5, 2),
    borderBottom: `1px solid ${theme.palette.divider}`,
    backgroundColor: alpha(theme.palette.background.paper, 0.6),
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    flexShrink: 0,
}));

const ScrollArea = styled(Box)({
    overflowY: "auto",
    maxHeight: 680,
});

const SamplePanel = styled(Box)(({ theme }) => ({
    padding: theme.spacing(1.5, 2),
    borderBottom: `1px solid ${theme.palette.divider}`,
    backgroundColor: theme.palette.background.default,
}));

const SpelOutput = styled("pre")(({ theme }) => ({
    margin: 0,
    padding: theme.spacing(2),
    fontSize: 12,
    lineHeight: 1.7,
    color: theme.palette.primary.light,
    overflowX: "auto",
    whiteSpace: "pre-wrap",
    wordBreak: "break-all",
    fontFamily: "monospace",
}));

const SpelEditorContainer = styled(Box)(({ theme }) => ({
    flex: 1,
    [`.${nodeValue}`]: { flex: 1, width: "100%" },
    [`.${rowAceEditor}`]: {
        padding: theme.spacing(0.75, 0.75),
        minHeight: 30,
        ".ace-nussknacker": { outline: "none" },
        "& .ace_placeholder": {
            color: theme.palette.text.disabled,
            fontStyle: "italic",
            fontSize: 12,
        },
    },
}));

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

// ─── Utilities ────────────────────────────────────────────────────────────────

function inferDisplayType(val: unknown): string {
    if (val === null || val === undefined) return "null";
    if (typeof val === "boolean") return "Boolean";
    if (typeof val === "number") return Number.isInteger(val) ? "Integer" : "Float";
    if (typeof val === "string") return "String";
    if (Array.isArray(val)) return `Array[${val.length > 0 ? inferDisplayType(val[0]) : "?"}]`;
    if (typeof val === "object") return "Map";
    return "?";
}

function inferNuType(val: unknown): NuType {
    if (val === null || val === undefined) return "String";
    if (typeof val === "boolean") return "Boolean";
    if (typeof val === "number") {
        if (Number.isInteger(val)) return val > 2147483647 || val < -2147483648 ? "Long" : "Integer";
        return "Double";
    }
    if (typeof val === "string") return "String";
    if (Array.isArray(val)) return "List";
    if (typeof val === "object") return "Map";
    return "Any";
}

function displayValue(value: unknown): string {
    if (value === null) return "null";
    if (typeof value === "boolean") return String(value);
    if (typeof value === "string") {
        const t = value.trim();
        return t.length > 28 ? t.substring(0, 28) + "…" : t || "(empty)";
    }
    if (typeof value === "number") return String(value);
    if (Array.isArray(value)) return `[${value.length}]`;
    if (typeof value === "object") return `{${Object.keys(value as object).length}}`;
    return "";
}

function fieldsFromSample(obj: unknown): Array<Omit<FieldDefinition, "id">> {
    if (typeof obj !== "object" || obj === null || Array.isArray(obj)) return [];
    return Object.entries(obj as Record<string, unknown>).map(([k, v]) => ({
        name: k,
        type: inferNuType(v),
        expression: "",
        mapEntries: [],
        useMapBuilder: false,
    }));
}

let _nextId = 1;
function makeField(name = "", type: NuType = "String"): FieldDefinition {
    return { id: _nextId++, name, type, expression: "", mapEntries: [], useMapBuilder: false };
}
function makeMapEntry(): MapEntryDef {
    return { id: _nextId++, key: "", expression: "" };
}

/** Convert a dragged path expression to null-safe SpEL: #a.b.c → #a?.b?.c */
function toNullSafe(spelExpr: string): string {
    if (!spelExpr.startsWith("#")) return spelExpr;
    return "#" + spelExpr.slice(1).replace(/\./g, "?.");
}

/** After Ace handles a drop it selects the inserted text. Clear selection and move cursor to end. */
function clearAceSelectionAfterDrop(container: EventTarget | null) {
    setTimeout(() => {
        const el = (container as HTMLElement | null)?.querySelector(".ace_editor") as {
            env?: { editor?: { clearSelection(): void; navigateFileEnd(): void } };
        } | null;
        el?.env?.editor?.clearSelection();
        el?.env?.editor?.navigateFileEnd();
    }, 0);
}

/** Split top-level comma-separated entries respecting nested braces. */
function splitTopLevel(inner: string): string[] {
    const parts: string[] = [];
    let braces = 0;
    let parens = 0;
    let brackets = 0;
    let start = 0;
    for (let i = 0; i < inner.length; i++) {
        const ch = inner[i];
        if (ch === "{") braces++;
        else if (ch === "}") braces--;
        else if (ch === "(") parens++;
        else if (ch === ")") parens--;
        else if (ch === "[") brackets++;
        else if (ch === "]") brackets--;
        else if (ch === "," && braces === 0 && parens === 0 && brackets === 0) {
            parts.push(inner.slice(start, i).trim());
            start = i + 1;
        }
    }
    parts.push(inner.slice(start).trim());
    return parts.filter(Boolean);
}

/** Parse a SpEL record expression `{ key: expr, ... }` back into FieldDefinition[]. */
function parseSpelToFields(expression: string): FieldDefinition[] | null {
    const trimmed = expression.trim();
    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null;
    const inner = trimmed.slice(1, -1).trim();
    const parts = splitTopLevel(inner);
    if (parts.length === 0) return [];

    const fields: FieldDefinition[] = [];
    for (const part of parts) {
        const colonIdx = part.indexOf(":");
        if (colonIdx === -1) continue;
        const name = part.slice(0, colonIdx).trim();
        const val = part.slice(colonIdx + 1).trim();
        if (!name) continue;

        // Nested record → Build Map mode
        if (val.startsWith("{") && val.endsWith("}")) {
            const mapParts = splitTopLevel(val.slice(1, -1).trim());
            const mapEntries: MapEntryDef[] = mapParts
                .map((mp) => {
                    const ci = mp.indexOf(":");
                    if (ci === -1) return null;
                    const k = mp.slice(0, ci).trim();
                    const v = mp.slice(ci + 1).trim();
                    return { id: _nextId++, key: k, expression: v };
                })
                .filter((e): e is MapEntryDef => e !== null);
            fields.push(makeField(name, "Map"));
            fields[fields.length - 1].mapEntries = mapEntries;
            fields[fields.length - 1].useMapBuilder = true;
            continue;
        }

        // null → empty field
        if (val === "null") {
            fields.push(makeField(name, "Any"));
            continue;
        }

        // Any SpEL expression (including #path)
        const field = makeField(name, "Any");
        field.expression = val;
        fields.push(field);
    }
    return fields;
}

// ─── TreeNode ─────────────────────────────────────────────────────────────────

interface TreeNodeProps {
    name: string;
    value: unknown;
    path: string;
    depth: number;
    onSelect: (path: string) => void;
    selectedPath: string | null;
    filterText?: string;
}

function treeNodeMatchesFilter(name: string, value: unknown, filterText: string): boolean {
    if (name.toLowerCase().includes(filterText)) return true;
    if (value !== null && typeof value === "object" && !Array.isArray(value)) {
        return Object.entries(value as Record<string, unknown>).some(([k, v]) => treeNodeMatchesFilter(k, v, filterText));
    }
    return false;
}

function TreeNode({ name, value, path, depth, onSelect, selectedPath, filterText }: TreeNodeProps): React.JSX.Element {
    const theme = useTheme();
    const filter = filterText?.toLowerCase() ?? "";
    const [open, setOpen] = useState(depth < 2);

    // When filter active, keep nodes that match or have matching descendants open
    const forceOpen = filter.length > 0;

    const isObj = value !== null && typeof value === "object" && !Array.isArray(value);
    const isArr = Array.isArray(value);
    const isExpandable = isObj || isArr;
    const isSelected = selectedPath === path;
    const isTopLevel = depth === 0;

    const children: [string, unknown][] = isObj
        ? Object.entries(value as Record<string, unknown>)
        : isArr
        ? (value as unknown[]).map((v, i) => [String(i), v])
        : [];

    // Filter children when search is active
    const visibleChildren = filter ? children.filter(([k, v]) => treeNodeMatchesFilter(k, v, filter)) : children;

    const handleClick = () => {
        if (isExpandable) setOpen((o) => !o);
        else onSelect(path);
    };

    return (
        <Box>
            <Box
                onClick={handleClick}
                draggable
                onDragStart={(e) => {
                    e.dataTransfer.setData("text/plain", toNullSafe(`#${path}`));
                }}
                sx={{
                    display: "flex",
                    alignItems: "center",
                    px: 1,
                    py: isTopLevel ? "4px" : "3px",
                    pl: `${8 + depth * 16}px`,
                    cursor: "grab",
                    borderRadius: 1,
                    mb: "1px",
                    mt: isTopLevel ? "2px" : 0,
                    backgroundColor: isTopLevel
                        ? isSelected
                            ? alpha(theme.palette.primary.main, 0.2)
                            : alpha(theme.palette.primary.main, 0.07)
                        : isSelected
                        ? alpha(theme.palette.primary.main, 0.15)
                        : "transparent",
                    border: `1px solid ${
                        isSelected ? theme.palette.primary.main : isTopLevel ? alpha(theme.palette.primary.main, 0.25) : "transparent"
                    }`,
                    "&:hover": {
                        backgroundColor: isSelected ? alpha(theme.palette.primary.main, 0.2) : alpha(theme.palette.action.hover, 0.5),
                    },
                }}
            >
                <Box sx={{ width: 18, flexShrink: 0, display: "flex", alignItems: "center" }}>
                    {isExpandable &&
                        (forceOpen || open ? (
                            <ExpandMoreIcon sx={{ fontSize: 14, color: "text.secondary" }} />
                        ) : (
                            <ChevronRightIcon sx={{ fontSize: 14, color: "text.secondary" }} />
                        ))}
                </Box>
                <Typography
                    sx={{ fontSize: 12, fontWeight: isTopLevel ? 700 : 500, color: isTopLevel ? "primary.main" : "text.primary", mr: 1 }}
                >
                    {name}
                </Typography>
                <Chip
                    label={inferDisplayType(value).split("[")[0]}
                    size="small"
                    sx={{
                        height: 16,
                        fontSize: 10,
                        mr: 1,
                        backgroundColor: alpha(theme.palette.primary.main, 0.15),
                        color: theme.palette.primary.light,
                        "& .MuiChip-label": { px: "5px" },
                    }}
                />
                {!isExpandable && (
                    <Typography
                        sx={{
                            fontSize: 11,
                            color: "text.disabled",
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                            flex: 1,
                        }}
                    >
                        {displayValue(value)}
                    </Typography>
                )}
            </Box>
            <Collapse in={forceOpen || open}>
                {isExpandable &&
                    visibleChildren.map(([k, v]) => (
                        <TreeNode
                            key={k}
                            name={k}
                            value={v}
                            path={isArr ? `${path}[${k}]` : `${path}.${k}`}
                            depth={depth + 1}
                            onSelect={onSelect}
                            selectedPath={selectedPath}
                            filterText={filterText}
                        />
                    ))}
            </Collapse>
        </Box>
    );
}

// ─── MapEntryRow ──────────────────────────────────────────────────────────────

interface MapEntryRowProps {
    entry: MapEntryDef;
    variableTypes: VariableTypes;
    onChange: (key: keyof MapEntryDef, value: unknown) => void;
    onRemove: () => void;
}

function MapEntryRow({ entry, variableTypes, onChange, onRemove }: MapEntryRowProps): React.JSX.Element {
    const onChangeRef = useRef(onChange);
    onChangeRef.current = onChange;
    const onExpressionChange = useCallback((val: string) => onChangeRef.current("expression", val), []);

    return (
        <Box
            sx={{ display: "flex", alignItems: "center", gap: 0.75, mb: 0.5 }}
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
                e.preventDefault();
                const p = e.dataTransfer.getData("text/plain");
                if (p) onChange("expression", p);
            }}
        >
            <TextField
                value={entry.key}
                onChange={(e) => onChange("key", e.target.value)}
                placeholder="key"
                size="small"
                sx={{ width: 110, "& .MuiInputBase-input": { fontSize: 11, fontFamily: "monospace", py: "3px" } }}
            />
            <Typography sx={{ fontSize: 11, color: "text.secondary", flexShrink: 0 }}>:</Typography>
            <SpelEditorContainer onDrop={(e) => clearAceSelectionAfterDrop(e.currentTarget)}>
                <ExpressionSuggest
                    inputProps={{
                        value: entry.expression,
                        language: ExpressionLang.SpEL,
                        onValueChange: onExpressionChange,
                        rows: 1,
                        placeholder: "#input.field",
                    }}
                    variableTypes={variableTypes}
                    fieldErrors={[]}
                />
            </SpelEditorContainer>
            <IconButton size="small" onClick={onRemove} sx={{ p: "2px", color: "text.disabled", "&:hover": { color: "error.main" } }}>
                <DeleteIcon sx={{ fontSize: 13 }} />
            </IconButton>
        </Box>
    );
}

// ─── FieldRow ─────────────────────────────────────────────────────────────────

interface FieldRowProps {
    field: FieldDefinition;
    isSelected: boolean;
    isDragOver: boolean;
    variableTypes: VariableTypes;
    onSelect: () => void;
    onChange: (key: keyof FieldDefinition, value: unknown) => void;
    onMapEntryAdd: () => void;
    onRemove: () => void;
    onMoveUp: () => void;
    onMoveDown: () => void;
    onDragOver: () => void;
    onDragLeave: () => void;
    onDrop: (path: string) => void;
}

function FieldRow({
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
                                            id: _nextId++,
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

// ─── SampleJsonPanel ──────────────────────────────────────────────────────────

interface SampleJsonPanelProps {
    title: string;
    placeholder: string;
    mergeLabel?: string;
    onApply: (parsed: unknown, mode: "replace" | "merge") => string | null;
    onClose: () => void;
}

function SampleJsonPanel({ title, placeholder, mergeLabel, onApply, onClose }: SampleJsonPanelProps): React.JSX.Element {
    const [text, setText] = useState("");
    const [error, setError] = useState("");
    const [mode, setMode] = useState<"replace" | "merge">("replace");

    const handleApply = () => {
        const trimmed = text.trim();
        if (!trimmed) {
            setError("Paste a JSON object first");
            return;
        }
        let parsed: unknown;
        try {
            parsed = JSON.parse(trimmed);
        } catch (e) {
            setError(`Invalid JSON: ${(e as Error).message}`);
            return;
        }
        const err = onApply(parsed, mode);
        if (err) {
            setError(err);
            return;
        }
        setText("");
        setError("");
        onClose();
    };

    return (
        <SamplePanel>
            <Typography sx={{ fontSize: 12, mb: 1 }}>{title}</Typography>
            <TextField
                value={text}
                onChange={(e) => {
                    setText(e.target.value);
                    setError("");
                }}
                placeholder={placeholder}
                multiline
                minRows={5}
                fullWidth
                size="small"
                sx={{ mb: 1, "& .MuiInputBase-input": { fontSize: 12, fontFamily: "monospace" } }}
            />
            {error && <Typography sx={{ fontSize: 11, color: "error.light", mb: 1 }}>{error}</Typography>}
            <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
                <Button
                    size="small"
                    variant={mode === "replace" ? "contained" : "outlined"}
                    onClick={() => setMode("replace")}
                    sx={{ fontSize: 11, textTransform: "none", py: "2px" }}
                >
                    Replace all
                </Button>
                {mergeLabel && (
                    <Button
                        size="small"
                        variant={mode === "merge" ? "contained" : "outlined"}
                        onClick={() => setMode("merge")}
                        sx={{ fontSize: 11, textTransform: "none", py: "2px" }}
                    >
                        {mergeLabel}
                    </Button>
                )}
                <Box sx={{ flex: 1 }} />
                <Button size="small" variant="contained" color="success" onClick={handleApply} sx={{ fontSize: 12, textTransform: "none" }}>
                    Apply
                </Button>
                <Button size="small" variant="outlined" onClick={onClose} sx={{ fontSize: 12, textTransform: "none" }}>
                    Cancel
                </Button>
            </Box>
        </SamplePanel>
    );
}

// ─── TopicPickerPanel ─────────────────────────────────────────────────────────

interface TopicPickerPanelProps {
    loading: boolean;
    entries: TopicEntry[];
    onSelect: (entry: TopicEntry) => void;
    onClose: () => void;
}

function TopicPickerPanel({ loading, entries, onSelect, onClose }: TopicPickerPanelProps): React.JSX.Element {
    const theme = useTheme();
    return (
        <SamplePanel>
            <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 1 }}>
                <Typography sx={{ fontSize: 12 }}>Select a topic to load its output schema as target fields:</Typography>
                <Button size="small" variant="outlined" onClick={onClose} sx={{ fontSize: 11, textTransform: "none", py: "2px" }}>
                    Cancel
                </Button>
            </Box>
            {loading && <Typography sx={{ fontSize: 12, color: "text.secondary", py: 1 }}>Loading topic schemas…</Typography>}
            {!loading && entries.length === 0 && (
                <Typography sx={{ fontSize: 12, color: "text.secondary", py: 1 }}>No topics with defined schemas found.</Typography>
            )}
            {!loading && entries.length > 0 && (
                <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.75 }}>
                    {entries.map((e) => {
                        const fieldCount = Object.keys(e.schema).length;
                        return (
                            <Chip
                                key={e.topic}
                                label={`${e.topic} (${fieldCount} fields)`}
                                size="small"
                                clickable
                                onClick={() => onSelect(e)}
                                sx={{
                                    fontSize: 12,
                                    backgroundColor: alpha(theme.palette.primary.main, 0.12),
                                    color: theme.palette.primary.light,
                                    "&:hover": { backgroundColor: alpha(theme.palette.primary.main, 0.25) },
                                }}
                            />
                        );
                    })}
                </Box>
            )}
        </SamplePanel>
    );
}

// ─── Type-based context enrichment ───────────────────────────────────────────

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function typingResultToSample(t: any): unknown {
    if (!t) return null;
    // Fields present → it's a record type (TypedObjectTypingResult), regardless of type discriminator
    if (t.fields && typeof t.fields === "object" && Object.keys(t.fields).length > 0) {
        return Object.fromEntries(Object.entries(t.fields as Record<string, unknown>).map(([k, v]) => [k, typingResultToSample(v)]));
    }
    const name: string = t.refClazzName ?? "";
    if (name.includes("List") || name.includes("Collection")) {
        const elem = t.params?.[0];
        return elem ? [typingResultToSample(elem)] : [];
    }
    if (name === "java.lang.String") return "";
    if (name === "java.lang.Boolean") return false;
    if (name.includes("Integer") || name.includes("Long") || name.includes("Short") || name.includes("Double") || name.includes("Float"))
        return 0;
    if (name.includes("Map")) return {};
    return null;
}

// ─── DataMapper ───────────────────────────────────────────────────────────────

const INITIAL_FIELDS: FieldDefinition[] = [
    makeField("icao24", "String"),
    makeField("callsign", "String"),
    makeField("origin_country", "String"),
    makeField("time_position", "Integer"),
    makeField("last_contact", "Integer"),
    makeField("longitude", "Float"),
    makeField("latitude", "Float"),
    makeField("baro_altitude", "Float"),
    makeField("on_ground", "Boolean"),
    makeField("velocity", "Float"),
    makeField("true_track", "Float"),
    makeField("vertical_rate", "Float"),
    makeField("geo_altitude", "Float"),
    makeField("squawk", "String"),
];

export type { ContextData };

export interface TopicEntry {
    topic: string;
    schema: Record<string, unknown>;
}

interface DataMapperProps {
    onInsert?: (spel: string) => void;
    initialContext?: ContextData;
    initialExpression?: string;
    variableTypes?: VariableTypes;
    /** Override the default topic fetching (which uses a generic kafka sink probe). */
    fetchTopicDefinitions?: () => Promise<TopicEntry[]>;
}

const KAFKA_TOPIC_PROBE_NODE = {
    type: "Sink",
    id: "_spel-mapper-probe",
    ref: { typ: "kafka", parameters: [{ name: "Topic", expression: { language: "spel", expression: "''" } }] },
    additionalFields: { layoutData: { x: 0, y: 0 }, description: "" },
    endResult: null,
    isDisabled: null,
    branchParametersTemplate: [],
} as const;

export function DataMapper({
    onInsert,
    initialContext,
    initialExpression,
    variableTypes,
    fetchTopicDefinitions: fetchTopicDefinitionsOverride,
}: DataMapperProps = {}): React.JSX.Element {
    // When variableTypes are provided the context is built from them — no need for hardcoded demo data.
    const [context, setContext] = useState<ContextData>(() => initialContext ?? (variableTypes ? {} : SAMPLE_CONTEXT));

    // Enrich context with type information: fills in empty/missing variables using variableTypes
    // so that List[Record{...}] variables show their element structure in the tree
    const enrichedContext = useMemo<ContextData>(() => {
        if (!variableTypes) return context;
        const merged: ContextData = { ...context };
        for (const [key, typingResult] of Object.entries(variableTypes)) {
            const existing = merged[key];
            const elem0 = Array.isArray(existing) ? existing[0] : undefined;
            const lacksStructure =
                existing === undefined ||
                (Array.isArray(existing) && existing.length === 0) ||
                // Array whose first element has no explorable fields (null, primitive, or empty object)
                (Array.isArray(existing) &&
                    existing.length > 0 &&
                    (elem0 === null || typeof elem0 !== "object" || Object.keys(elem0 as object).length === 0));
            if (lacksStructure) {
                const sample = typingResultToSample(typingResult);
                if (sample !== null) merged[key] = sample;
            }
        }
        return merged;
    }, [context, variableTypes]);
    const processName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);
    const [fields, setFields] = useState<FieldDefinition[]>(() => {
        if (initialExpression) {
            const parsed = parseSpelToFields(initialExpression);
            if (parsed) return parsed;
        }
        return onInsert ? [] : INITIAL_FIELDS;
    });
    const [selField, setSelField] = useState<number | null>(null);
    const [selPath, setSelPath] = useState<string | null>(null);
    const [dragOverId, setDragOverId] = useState<number | null>(null);
    const [showTargetSample, setShowTargetSample] = useState(false);
    const [showContextSample, setShowContextSample] = useState(false);
    const [showTopicPicker, setShowTopicPicker] = useState(false);
    const [topicEntries, setTopicEntries] = useState<TopicEntry[]>([]);
    const [topicsLoading, setTopicsLoading] = useState(false);
    const [contextFilter, setContextFilter] = useState("");
    const [dropZoneActive, setDropZoneActive] = useState(false);

    const addField = () => setFields((f) => [...f, makeField()]);

    const addFieldFromDrop = (path: string) => {
        const lastSegment = path.split(".").pop()?.replace(/\?/g, "") ?? "";
        const field = makeField(lastSegment);
        field.expression = path;
        setFields((f) => [...f, field]);
        setDragOverId(null);
        setDropZoneActive(false);
    };

    const removeField = (id: number) => {
        setFields((f) => f.filter((x) => x.id !== id));
        if (selField === id) setSelField(null);
    };

    const updateField = useCallback((id: number, key: keyof FieldDefinition, val: unknown) => {
        setFields((f) => f.map((x) => (x.id !== id ? x : { ...x, [key]: val })));
    }, []);

    const handleAutoMap = useCallback(() => {
        // Flatten context to: normalizedFieldName -> full path
        const pathMap = new Map<string, string>();
        function traverse(obj: unknown, path: string) {
            if (obj !== null && typeof obj === "object" && !Array.isArray(obj)) {
                Object.entries(obj as Record<string, unknown>).forEach(([k, v]) => traverse(v, `${path}.${k}`));
            } else {
                const key = path.split(".").pop()!.toLowerCase().replace(/[_\s]/g, "");
                if (!pathMap.has(key)) pathMap.set(key, path);
            }
        }
        Object.entries(enrichedContext).forEach(([key, val]) => traverse(val, key));

        setFields((prev) =>
            prev.map((f) => {
                if (f.expression || (f.useMapBuilder && f.mapEntries.length > 0)) return f;
                const normalized = f.name.toLowerCase().replace(/[_\s]/g, "");
                const match = pathMap.get(normalized);
                return match ? { ...f, expression: toNullSafe(`#${match}`) } : f;
            }),
        );
    }, [enrichedContext]);

    const moveField = (id: number, dir: 1 | -1) => {
        setFields((f) => {
            const idx = f.findIndex((x) => x.id === id);
            if (idx < 0) return f;
            const ni = idx + dir;
            if (ni < 0 || ni >= f.length) return f;
            const c = [...f];
            [c[idx], c[ni]] = [c[ni], c[idx]];
            return c;
        });
    };

    const onTreeSelect = useCallback(
        (path: string) => {
            setSelPath(path);
            if (selField != null) {
                setFields((f) => f.map((x) => (x.id === selField ? { ...x, expression: toNullSafe(`#${path}`) } : x)));
            }
        },
        [selField],
    );

    const onDrop = (path: string, fieldId: number) => {
        const lastSegment = path.split(".").pop()?.replace(/\?/g, "") ?? "";
        setFields((prev) =>
            prev.map((x) => {
                if (x.id !== fieldId) return x;
                const nameUpdate = !x.name?.trim() && lastSegment ? { name: lastSegment } : {};
                return { ...x, expression: path, ...nameUpdate };
            }),
        );
        setDragOverId(null);
    };

    const applyTargetSample = (parsed: unknown, mode: "replace" | "merge"): string | null => {
        const newFields = fieldsFromSample(parsed);
        if (newFields.length === 0) return 'JSON must be a flat object, e.g. {"name": "value"}';
        if (mode === "replace") {
            setFields(newFields.map((f) => ({ ...f, id: _nextId++ })));
        } else {
            setFields((prev) => {
                const existing = new Set(prev.map((x) => x.name));
                const toAdd = newFields.filter((nf) => !existing.has(nf.name)).map((f) => ({ ...f, id: _nextId++ }));
                return [...prev, ...toAdd];
            });
        }
        return null;
    };

    const applyContextSample = (parsed: unknown, _mode: "replace" | "merge"): string | null => {
        if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
            return "Context JSON must be a top-level object where each key is a variable name";
        }
        setContext(parsed as ContextData);
        return null;
    };

    const defaultFetchTopicDefinitions = useCallback(async (): Promise<TopicEntry[]> => {
        if (!processName || !processProperties) return [];
        // First call: get topic list from any topic probe
        const probe = await HttpService.validateNode(processName, {
            nodeData: KAFKA_TOPIC_PROBE_NODE as never,
            variableTypes: {},
            branchVariableTypes: {},
            outgoingEdges: [],
            testCases: {},
            processProperties,
        });
        if (!probe) return [];
        const topicParam = probe.parameters?.find((p) => p.name === "Topic");
        const fixedEditor = topicParam?.editors?.find((e) => e.type === "FixedValuesParameterEditor") as
            | { possibleValues: { expression: string; label: string }[] }
            | undefined;
        const topics = (fixedEditor?.possibleValues ?? []).filter((pv) => pv.label && pv.expression && pv.expression !== "''");
        if (topics.length === 0) return [];

        // Parallel calls: one per topic to get its schema
        const results = await Promise.allSettled(
            topics.map(async ({ expression, label }) => {
                const nodeWithTopic = {
                    ...KAFKA_TOPIC_PROBE_NODE,
                    ref: { ...KAFKA_TOPIC_PROBE_NODE.ref, parameters: [{ name: "Topic", expression: { language: "spel", expression } }] },
                };
                const data = await HttpService.validateNode(processName, {
                    nodeData: nodeWithTopic as never,
                    variableTypes: {},
                    branchVariableTypes: {},
                    outgoingEdges: [],
                    testCases: {},
                    processProperties,
                });
                if (!data) return null;
                const valueParam = data.parameters?.find((p) => p.name === "Value");
                const defaultExpr = valueParam?.defaultValue?.expression;
                if (!defaultExpr) return null;
                try {
                    const parsed = JSON.parse(defaultExpr);
                    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed) || Object.keys(parsed).length === 0)
                        return null;
                    return { topic: label, schema: parsed as Record<string, unknown> };
                } catch {
                    return null;
                }
            }),
        );
        return results
            .filter((r): r is PromiseFulfilledResult<TopicEntry> => r.status === "fulfilled" && r.value !== null)
            .map((r) => r.value);
    }, [processName, processProperties]);

    const fetchTopicDefinitions = fetchTopicDefinitionsOverride ?? defaultFetchTopicDefinitions;

    const handleOpenTopicPicker = useCallback(async () => {
        setShowTopicPicker(true);
        setShowTargetSample(false);
        if (topicEntries.length > 0) return;
        setTopicsLoading(true);
        try {
            const entries = await fetchTopicDefinitions();
            setTopicEntries(entries);
        } finally {
            setTopicsLoading(false);
        }
    }, [fetchTopicDefinitions, topicEntries.length]);

    const applyTopicSchema = useCallback((entry: TopicEntry) => {
        const newFields = fieldsFromSample(entry.schema);
        if (newFields.length === 0) return;
        setFields(newFields.map((f) => ({ ...f, id: _nextId++ })));
        setShowTopicPicker(false);
    }, []);

    const addMapEntry = useCallback((fieldId: number) => {
        setFields((fs) => fs.map((f) => (f.id !== fieldId ? f : { ...f, mapEntries: [...f.mapEntries, makeMapEntry()] })));
    }, []);

    const genSpel = useCallback((): string => {
        const lines = fields.map((f) => {
            if (f.useMapBuilder && f.mapEntries.length > 0) {
                const entries = f.mapEntries.filter((e) => e.key).map((e) => `    ${e.key}: ${e.expression || "null"}`);
                return `  ${f.name}: {\n${entries.join(",\n")}\n  }`;
            }
            return `  ${f.name}: ${f.expression || "null"}`;
        });
        return `{\n${lines.join(",\n")}\n}`;
    }, [fields]);

    const mappedCount = useMemo(() => fields.filter((f) => f.expression || (f.useMapBuilder && f.mapEntries.length > 0)).length, [fields]);

    return (
        <RootBox embedded={!!onInsert}>
            {/* Header */}
            <Box
                sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: onInsert ? "flex-end" : "space-between",
                    flexShrink: 0,
                    px: 2.5,
                    pt: onInsert ? 1 : 2.5,
                    pb: onInsert ? 1 : 0,
                    borderBottom: onInsert ? 1 : 0,
                    borderColor: "divider",
                }}
            >
                {!onInsert && (
                    <Box>
                        <Typography variant="h5" sx={{ fontWeight: 600, lineHeight: 1.3 }}>
                            Data Mapper
                        </Typography>
                        <Typography variant="caption" sx={{ color: "text.secondary" }}>
                            Map context variables to a target record
                        </Typography>
                    </Box>
                )}
            </Box>

            {/* Scrollable content area (panels + SpEL output) */}
            <Box
                sx={
                    onInsert
                        ? { flex: 1, overflowY: "auto", minHeight: 0, display: "flex", flexDirection: "column", gap: 2, p: 2.5 }
                        : { display: "contents" }
                }
            >
                {/* Main panels */}
                <Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
                    {/* Left: Context Variables */}
                    <PanelPaper sx={{ width: 360, flexShrink: 0 }}>
                        <PanelHeader>
                            <Box>
                                <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Context Variables</Typography>
                                <Typography sx={{ fontSize: 11, color: "text.secondary" }}>Drag leaf nodes onto target fields</Typography>
                            </Box>
                            <Tooltip title="Load context from JSON sample">
                                <IconButton
                                    size="small"
                                    onClick={() => setShowContextSample((v) => !v)}
                                    color={showContextSample ? "primary" : "default"}
                                >
                                    <UploadFileIcon sx={{ fontSize: 16 }} />
                                </IconButton>
                            </Tooltip>
                        </PanelHeader>

                        <Collapse in={showContextSample}>
                            <SampleJsonPanel
                                title="Paste your full context JSON (top-level keys = variable names):"
                                placeholder={'{\n  "input": { "id": 1, "name": "foo" },\n  "http_output": { "statusCode": 200 }\n}'}
                                onApply={applyContextSample}
                                onClose={() => setShowContextSample(false)}
                            />
                        </Collapse>

                        <Box sx={{ px: 1, pt: 1, pb: 0.5 }}>
                            <TextField
                                size="small"
                                placeholder="Search…"
                                value={contextFilter}
                                onChange={(e) => setContextFilter(e.target.value)}
                                fullWidth
                                InputProps={{
                                    startAdornment: (
                                        <InputAdornment position="start">
                                            <SearchIcon sx={{ fontSize: 16, color: "text.disabled" }} />
                                        </InputAdornment>
                                    ),
                                }}
                                sx={{ "& .MuiInputBase-input": { fontSize: 12, py: "4px" } }}
                            />
                        </Box>
                        <ScrollArea sx={{ p: 1, pt: 0.5 }}>
                            {Object.entries(enrichedContext)
                                .filter(([key, val]) => !contextFilter || treeNodeMatchesFilter(key, val, contextFilter.toLowerCase()))
                                .map(([key, val]) => (
                                    <TreeNode
                                        key={key}
                                        name={`#${key}`}
                                        value={val}
                                        path={key}
                                        depth={0}
                                        onSelect={onTreeSelect}
                                        selectedPath={selPath}
                                        filterText={contextFilter}
                                    />
                                ))}
                        </ScrollArea>
                    </PanelPaper>

                    {/* Right: Target Record + Expression output */}
                    <Box sx={{ flex: 1, display: "flex", flexDirection: "column", gap: 2 }}>
                        <PanelPaper sx={{ backgroundColor: "transparent" }}>
                            <PanelHeader>
                                <Box>
                                    <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Target Record</Typography>
                                    <Chip
                                        label={`${mappedCount} / ${fields.length} mapped`}
                                        size="small"
                                        variant="outlined"
                                        sx={{ height: 16, fontSize: 10, "& .MuiChip-label": { px: "5px" } }}
                                    />
                                </Box>
                                <Box sx={{ display: "flex", gap: 0.75 }}>
                                    <Tooltip title="Auto-fill unmapped fields by matching field names to context variables">
                                        <Button
                                            startIcon={<AutoFixHighIcon />}
                                            size="small"
                                            variant="outlined"
                                            onClick={handleAutoMap}
                                            sx={{
                                                fontSize: 12,
                                                textTransform: "none",
                                                borderColor: "divider",
                                                color: "text.primary",
                                                "&:hover": { borderColor: "text.secondary" },
                                            }}
                                        >
                                            Auto-map
                                        </Button>
                                    </Tooltip>
                                    <Button
                                        startIcon={<AddIcon />}
                                        size="small"
                                        variant="outlined"
                                        onClick={addField}
                                        sx={{ fontSize: 12, textTransform: "none" }}
                                    >
                                        Add Field
                                    </Button>
                                    <Button
                                        size="small"
                                        variant={showTopicPicker ? "contained" : "outlined"}
                                        color={showTopicPicker ? "primary" : "inherit"}
                                        onClick={() => (showTopicPicker ? setShowTopicPicker(false) : handleOpenTopicPicker())}
                                        sx={{ fontSize: 12, textTransform: "none" }}
                                    >
                                        From Topic
                                    </Button>
                                    <Button
                                        startIcon={<UploadFileIcon />}
                                        size="small"
                                        variant={showTargetSample ? "contained" : "outlined"}
                                        color={showTargetSample ? "secondary" : "inherit"}
                                        onClick={() => {
                                            setShowTargetSample((v) => !v);
                                            setShowTopicPicker(false);
                                        }}
                                        sx={{ fontSize: 12, textTransform: "none" }}
                                    >
                                        From Sample
                                    </Button>
                                </Box>
                            </PanelHeader>

                            <Collapse in={showTargetSample}>
                                <SampleJsonPanel
                                    title="Paste a JSON object to auto-detect output fields and types:"
                                    placeholder={
                                        '{\n  "icao24": "4952c9",\n  "callsign": "LOT672",\n  "on_ground": true,\n  "velocity": 5.14\n}'
                                    }
                                    mergeLabel="Merge (add missing)"
                                    onApply={applyTargetSample}
                                    onClose={() => setShowTargetSample(false)}
                                />
                            </Collapse>
                            <Collapse in={showTopicPicker}>
                                <TopicPickerPanel
                                    loading={topicsLoading}
                                    entries={topicEntries}
                                    onSelect={applyTopicSchema}
                                    onClose={() => setShowTopicPicker(false)}
                                />
                            </Collapse>

                            <ScrollArea sx={{ p: 1 }}>
                                {fields.map((f) => (
                                    <FieldRow
                                        key={f.id}
                                        field={f}
                                        isSelected={selField === f.id}
                                        isDragOver={dragOverId === f.id}
                                        variableTypes={variableTypes ?? {}}
                                        onSelect={() => setSelField(selField === f.id ? null : f.id)}
                                        onChange={(key, val) => updateField(f.id, key, val)}
                                        onMapEntryAdd={() => addMapEntry(f.id)}
                                        onRemove={() => removeField(f.id)}
                                        onMoveUp={() => moveField(f.id, -1)}
                                        onMoveDown={() => moveField(f.id, 1)}
                                        onDragOver={() => setDragOverId(f.id)}
                                        onDragLeave={() => setDragOverId(null)}
                                        onDrop={(path) => onDrop(path, f.id)}
                                    />
                                ))}
                                {/* Drop zone — creates a new field when a context variable is dropped */}
                                <Box
                                    onDragOver={(e) => {
                                        e.preventDefault();
                                        setDropZoneActive(true);
                                    }}
                                    onDragLeave={() => setDropZoneActive(false)}
                                    onDrop={(e) => {
                                        e.preventDefault();
                                        const path = e.dataTransfer.getData("text/plain");
                                        if (path) addFieldFromDrop(path);
                                        else setDropZoneActive(false);
                                    }}
                                    sx={(theme) => ({
                                        mt: fields.length > 0 ? 0.5 : 0,
                                        minHeight: fields.length === 0 ? 120 : 40,
                                        borderRadius: 1,
                                        border: `2px dashed ${dropZoneActive ? theme.palette.primary.main : "transparent"}`,
                                        backgroundColor: dropZoneActive ? alpha(theme.palette.primary.main, 0.06) : "transparent",
                                        transition: "border-color 0.15s, background-color 0.15s",
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "center",
                                    })}
                                >
                                    {fields.length === 0 && !dropZoneActive && (
                                        <Box sx={{ textAlign: "center", color: "text.disabled" }}>
                                            <Typography sx={{ fontSize: 12 }}>No output fields yet.</Typography>
                                            <Typography sx={{ fontSize: 11 }}>
                                                Drag a variable here or use <strong>Add Field</strong> above.
                                            </Typography>
                                        </Box>
                                    )}
                                    {dropZoneActive && (
                                        <Typography sx={{ fontSize: 11, color: "primary.main" }}>Drop to add field</Typography>
                                    )}
                                </Box>
                            </ScrollArea>
                        </PanelPaper>

                        {/* Expression output — secondary/muted panel */}
                        <PanelPaper
                            sx={{ opacity: 0.65, "&:hover": { opacity: 1 }, transition: "opacity 0.2s", backgroundColor: "transparent" }}
                        >
                            <PanelHeader>
                                <Typography sx={{ fontSize: 12, fontWeight: 500, color: "text.secondary" }}>Expression</Typography>
                                <Tooltip title="Copy to clipboard">
                                    <IconButton size="small" onClick={() => navigator.clipboard?.writeText(genSpel())}>
                                        <ContentCopyIcon sx={{ fontSize: 16 }} />
                                    </IconButton>
                                </Tooltip>
                            </PanelHeader>
                            <SpelOutput>{genSpel()}</SpelOutput>
                        </PanelPaper>
                    </Box>
                </Box>
            </Box>
            {/* end scrollable content */}

            {/* Footer */}
            <Box
                sx={(theme) => ({
                    flexShrink: 0,
                    display: "flex",
                    alignItems: "center",
                    gap: 3,
                    px: 2.5,
                    py: 1,
                    borderTop: `1px solid ${theme.palette.divider}`,
                    backgroundColor: alpha(theme.palette.background.paper, 0.6),
                })}
            >
                <Typography variant="caption" color="text.secondary">
                    Drag source variable → target field to map
                </Typography>
                <Typography variant="caption" color="text.secondary">
                    Click field to edit name, type, or write an expression with autocomplete
                </Typography>
                <Box sx={{ flex: 1 }} />
                {onInsert && (
                    <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        onClick={() => onInsert(genSpel())}
                        sx={{ textTransform: "none" }}
                    >
                        Apply
                    </Button>
                )}
            </Box>
        </RootBox>
    );
}

export default DataMapper;
