import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { alpha, Box, Chip, Collapse, Typography, useTheme } from "@mui/material";
import React, { useState } from "react";

import type { TypingResult } from "../../types/definition";
import { inferDisplayType, treeNodeMatchesFilter, toNullSafe } from "./typeUtils";

export interface ContextTreeNodeProps {
    name: string;
    value: unknown;
    path: string;
    depth: number;
    /** Called on leaf click (or any node click in non-expandable mode). Was `onInsert` in ConditionBuilder. */
    onSelect: (path: string) => void;
    /** Optional: highlight the selected path (used by DataMapper). */
    selectedPath?: string | null;
    /** Optional: filter text for highlighting/filtering (used by DataMapper). */
    filterText?: string;
    /** When true, forces all expandable nodes open regardless of local state (used by DataMapper). */
    forceOpen?: boolean;
    /** When true, prepends `#` and applies toNullSafe to drag data (used by DataMapper). */
    nullSafeDrag?: boolean;
    /** When true, renders this node with a warning/accent color to indicate it's a special variable (e.g. #ROW). */
    highlight?: boolean;
    /** TypingResult of this node's value; used for type-aware path generation (SpEL picker). */
    typing?: TypingResult;
    /** When true, clicking an expandable node also calls onSelect (not just toggles expand). */
    selectExpandable?: boolean;
    /** When true, uses type-aware path notation: `?.key` for typed objects, `['key']` for Unknown/Map types. */
    useTypedPaths?: boolean;
    /** When false and useTypedPaths is true, typed objects use `.key` instead of `?.key`. Defaults to true. */
    nullSafe?: boolean;
    /** When true, insertion requires double-click instead of single-click (prevents accidental inserts on tree expand). */
    doubleClickToInsert?: boolean;
}

function hasKnownFields(typing: TypingResult | undefined): boolean {
    if (!typing) return false;
    const fields = (typing as { fields?: unknown }).fields;
    return !!fields && typeof fields === "object" && Object.keys(fields as object).length > 0;
}

function getChildTyping(typing: TypingResult | undefined, key: string): TypingResult | undefined {
    if (!typing) return undefined;
    const fields = (typing as { fields?: Record<string, TypingResult> }).fields;
    return fields?.[key];
}

export function ContextTreeNode({
    name,
    value,
    path,
    depth,
    onSelect,
    selectedPath,
    filterText,
    forceOpen: forceOpenProp,
    nullSafeDrag,
    highlight,
    typing,
    selectExpandable,
    useTypedPaths,
    nullSafe = true,
    doubleClickToInsert,
}: ContextTreeNodeProps): React.JSX.Element {
    const theme = useTheme();
    const filter = filterText?.toLowerCase() ?? "";
    const [open, setOpen] = useState(depth < 2);

    const forceOpen = forceOpenProp ?? filter.length > 0;

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

    const visibleChildren = filter ? children.filter(([k, v]) => treeNodeMatchesFilter(k, v, filter)) : children;

    const handleClick = () => {
        if (isExpandable) {
            setOpen((o) => !o);
        } else if (!doubleClickToInsert) {
            onSelect(path);
        }
    };

    const handleDoubleClick = doubleClickToInsert ? () => onSelect(path) : undefined;

    const dragData = nullSafeDrag ? toNullSafe(`#${path}`) : path;

    return (
        <Box>
            <Box
                onClick={handleClick}
                onDoubleClick={handleDoubleClick}
                draggable
                onDragStart={(e) => {
                    e.dataTransfer.setData("text/plain", dragData);
                }}
                sx={{
                    display: "flex",
                    alignItems: "center",
                    px: 1,
                    py: isTopLevel ? "4px" : "3px",
                    pl: `${8 + depth * 16}px`,
                    cursor: isExpandable ? "pointer" : doubleClickToInsert ? "default" : "grab",
                    borderRadius: 1,
                    mb: "1px",
                    mt: isTopLevel ? "2px" : 0,
                    backgroundColor: isTopLevel
                        ? isSelected
                            ? alpha(highlight ? theme.palette.warning.main : theme.palette.primary.main, 0.2)
                            : alpha(highlight ? theme.palette.warning.main : theme.palette.primary.main, 0.07)
                        : isSelected
                        ? alpha(theme.palette.primary.main, 0.15)
                        : "transparent",
                    border: `1px solid ${
                        isSelected
                            ? highlight
                                ? theme.palette.warning.main
                                : theme.palette.primary.main
                            : isTopLevel
                            ? alpha(highlight ? theme.palette.warning.main : theme.palette.primary.main, 0.25)
                            : "transparent"
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
                    sx={{
                        fontSize: 12,
                        fontWeight: isTopLevel ? 700 : 500,
                        color: isTopLevel ? (highlight ? "warning.main" : "primary.main") : "text.primary",
                        mr: 1,
                    }}
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
                        backgroundColor: alpha(highlight ? theme.palette.warning.main : theme.palette.primary.main, 0.15),
                        color: highlight ? theme.palette.warning.main : theme.palette.primary.light,
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
                    visibleChildren.map(([k, v]) => {
                        const childPath = isArr
                            ? `${path}[${k}]`
                            : useTypedPaths
                            ? hasKnownFields(typing)
                                ? nullSafe
                                    ? `${path}?.${k}`
                                    : `${path}.${k}`
                                : `${path}['${k}']`
                            : `${path}.${k}`;
                        return (
                            <ContextTreeNode
                                key={k}
                                name={k}
                                value={v}
                                path={childPath}
                                depth={depth + 1}
                                onSelect={onSelect}
                                selectedPath={selectedPath}
                                filterText={filterText}
                                forceOpen={forceOpenProp}
                                nullSafeDrag={nullSafeDrag}
                                typing={useTypedPaths ? getChildTyping(typing, k) : undefined}
                                selectExpandable={selectExpandable}
                                useTypedPaths={useTypedPaths}
                                nullSafe={nullSafe}
                                doubleClickToInsert={doubleClickToInsert}
                            />
                        );
                    })}
            </Collapse>
        </Box>
    );
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
