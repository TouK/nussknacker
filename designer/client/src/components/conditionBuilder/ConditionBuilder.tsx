import AddIcon from "@mui/icons-material/Add";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import DeleteIcon from "@mui/icons-material/Delete";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import SearchIcon from "@mui/icons-material/Search";
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

import type { VariableTypes } from "../../types/validation";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import { rowAceEditor, nodeValue } from "../graph/node-modal/NodeDetailsContent/NodeTableStyled";

// ─── Types ────────────────────────────────────────────────────────────────────

type Operator = "==" | "!=" | "<" | "<=" | ">" | ">=" | "== null" | "!= null";
type Combinator = "&&" | "||";

interface Condition {
    id: number;
    left: string;
    operator: Operator;
    right: string;
}

interface ParsedResult {
    combinator: Combinator;
    conditions: Omit<Condition, "id">[];
}

// ─── Constants ────────────────────────────────────────────────────────────────

const OPERATORS: { value: Operator; label: string }[] = [
    { value: "==", label: "equals" },
    { value: "!=", label: "not equals" },
    { value: "<", label: "less than" },
    { value: "<=", label: "less than or equal" },
    { value: ">", label: "greater than" },
    { value: ">=", label: "greater than or equal" },
    { value: "== null", label: "is null" },
    { value: "!= null", label: "is not null" },
];

const NULL_OPERATORS = new Set<Operator>(["== null", "!= null"]);

// ─── Styled components ────────────────────────────────────────────────────────

const RootBox = styled(Box)(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    flex: 1,
    minHeight: 0,
    overflow: "hidden",
    height: "100%",
    backgroundColor: theme.palette.background.default,
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
    maxHeight: 480,
});

const SpelOutput = styled("pre")(({ theme }) => ({
    margin: 0,
    padding: theme.spacing(1.5, 2),
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
    minWidth: 0,
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

// ─── Utilities ────────────────────────────────────────────────────────────────

/** After Ace handles a drop it selects the inserted text. Clear selection so cursor is at end and typing continues. */
function clearAceSelectionAfterDrop(container: EventTarget | null) {
    setTimeout(() => {
        const el = (container as HTMLElement | null)?.querySelector(".ace_editor") as {
            env?: { editor?: { clearSelection(): void } };
        } | null;
        el?.env?.editor?.clearSelection();
    });
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function typingResultToSample(t: any): unknown {
    if (!t) return null;
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

function inferDisplayType(val: unknown): string {
    if (val === null || val === undefined) return "null";
    if (typeof val === "boolean") return "Boolean";
    if (typeof val === "number") return Number.isInteger(val) ? "Integer" : "Float";
    if (typeof val === "string") return "String";
    if (Array.isArray(val)) return "List";
    if (typeof val === "object") return "Map";
    return "?";
}

function findOpIndex(expr: string, op: string): number {
    let depth = 0;
    for (let i = 0; i <= expr.length - op.length; i++) {
        const ch = expr[i];
        if (ch === "(" || ch === "[") {
            depth++;
            continue;
        }
        if (ch === ")" || ch === "]") {
            depth--;
            continue;
        }
        if (depth === 0 && expr.startsWith(op, i)) {
            // Don't match < inside <= or > inside >=
            if (op === "<" && expr[i + 1] === "=") continue;
            if (op === ">" && expr[i + 1] === "=") continue;
            return i;
        }
    }
    return -1;
}

function parseConditionPart(part: string): Omit<Condition, "id"> | null {
    const trimmed = part.trim();
    // Try operators in precedence order (longer first to avoid partial matches)
    const opsToTry: Operator[] = ["<=", ">=", "==", "!=", "<", ">"];
    for (const op of opsToTry) {
        const idx = findOpIndex(trimmed, op);
        if (idx !== -1) {
            const left = trimmed.slice(0, idx).trim();
            const rightRaw = trimmed.slice(idx + op.length).trim();
            // Check for null suffix
            if (rightRaw === "null" && op === "==") {
                return { left, operator: "== null", right: "" };
            }
            if (rightRaw === "null" && op === "!=") {
                return { left, operator: "!= null", right: "" };
            }
            return { left, operator: op as Operator, right: rightRaw };
        }
    }
    return null;
}

function parseSpel(expression: string): ParsedResult | null {
    const trimmed = expression.trim();
    if (!trimmed) return null;

    // Detect top-level combinator
    let combinator: Combinator = "&&";
    let splitOn = "";

    // Scan for && or || at depth 0
    let depth = 0;
    let foundCombinator: Combinator | null = null;
    for (let i = 0; i < trimmed.length - 1; i++) {
        const ch = trimmed[i];
        if (ch === "(" || ch === "[") {
            depth++;
            continue;
        }
        if (ch === ")" || ch === "]") {
            depth--;
            continue;
        }
        if (depth === 0) {
            if (trimmed[i] === "&" && trimmed[i + 1] === "&") {
                foundCombinator = "&&";
                break;
            }
            if (trimmed[i] === "|" && trimmed[i + 1] === "|") {
                foundCombinator = "||";
                break;
            }
        }
    }

    if (foundCombinator) {
        combinator = foundCombinator;
        splitOn = foundCombinator;
    }

    // Split by the combinator at depth 0
    let parts: string[];
    if (!splitOn) {
        parts = [trimmed];
    } else {
        parts = [];
        let current = "";
        let d = 0;
        for (let i = 0; i < trimmed.length; i++) {
            const ch = trimmed[i];
            if (ch === "(" || ch === "[") {
                d++;
                current += ch;
            } else if (ch === ")" || ch === "]") {
                d--;
                current += ch;
            } else if (d === 0 && trimmed.startsWith(splitOn, i)) {
                parts.push(current.trim());
                current = "";
                i += splitOn.length - 1;
            } else {
                current += ch;
            }
        }
        if (current.trim()) parts.push(current.trim());
    }

    const conditions: Omit<Condition, "id">[] = [];
    for (const part of parts) {
        const cond = parseConditionPart(part);
        if (!cond) return null;
        conditions.push(cond);
    }

    if (conditions.length === 0) return null;
    return { combinator, conditions };
}

function genSpel(conditions: Condition[], combinator: Combinator): string {
    if (conditions.length === 0) return "";
    const parts = conditions.map((c) => {
        if (NULL_OPERATORS.has(c.operator)) {
            return `${c.left} ${c.operator}`;
        }
        return `${c.left} ${c.operator} ${c.right}`;
    });
    return parts.join(` ${combinator} `);
}

let _nextId = 1;
function makeCondition(partial: Partial<Omit<Condition, "id">> = {}): Condition {
    return { id: _nextId++, left: "", operator: "==", right: "", ...partial };
}

// ─── Context tree ─────────────────────────────────────────────────────────────

interface ContextTreeNodeProps {
    name: string;
    value: unknown;
    path: string;
    depth: number;
    onInsert: (path: string) => void;
}

function ContextTreeNode({ name, value, path, depth, onInsert }: ContextTreeNodeProps): React.JSX.Element {
    const theme = useTheme();
    const isObj = value !== null && typeof value === "object" && !Array.isArray(value);
    const isArr = Array.isArray(value);
    const isExpandable = isObj || isArr;
    const isTopLevel = depth === 0;
    const [open, setOpen] = useState(depth < 1);

    const children: [string, unknown][] = isObj
        ? Object.entries(value as Record<string, unknown>)
        : isArr
        ? (value as unknown[]).slice(0, 1).map((v, i) => [String(i), v])
        : [];

    const handleClick = () => {
        if (isExpandable) {
            setOpen((o) => !o);
        } else {
            onInsert(path);
        }
    };

    return (
        <Box>
            <Box
                onClick={handleClick}
                draggable
                onDragStart={(e) => e.dataTransfer.setData("text/plain", path)}
                sx={{
                    display: "flex",
                    alignItems: "center",
                    px: 1,
                    py: isTopLevel ? "4px" : "3px",
                    pl: `${8 + depth * 16}px`,
                    cursor: isExpandable ? "pointer" : "grab",
                    borderRadius: 1,
                    mb: "1px",
                    mt: isTopLevel ? "2px" : 0,
                    backgroundColor: isTopLevel ? alpha(theme.palette.primary.main, 0.07) : "transparent",
                    border: `1px solid ${isTopLevel ? alpha(theme.palette.primary.main, 0.25) : "transparent"}`,
                    "&:hover": {
                        backgroundColor: alpha(theme.palette.action.hover, 0.5),
                    },
                }}
            >
                <Box sx={{ width: 18, flexShrink: 0, display: "flex", alignItems: "center" }}>
                    {isExpandable &&
                        (open ? (
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
                    label={inferDisplayType(value)}
                    size="small"
                    sx={{
                        height: 16,
                        fontSize: 10,
                        backgroundColor: alpha(theme.palette.primary.main, 0.15),
                        color: theme.palette.primary.light,
                        "& .MuiChip-label": { px: "5px" },
                    }}
                />
            </Box>
            <Collapse in={open}>
                {isExpandable &&
                    children.map(([k, v]) => (
                        <ContextTreeNode
                            key={k}
                            name={k}
                            value={v}
                            path={isArr ? `${path}[${k}]` : `${path}.${k}`}
                            depth={depth + 1}
                            onInsert={onInsert}
                        />
                    ))}
            </Collapse>
        </Box>
    );
}

// ─── ConditionBuilder ─────────────────────────────────────────────────────────

export interface ConditionBuilderProps {
    onInsert?: (spel: string) => void;
    initialExpression?: string;
    variableTypes?: VariableTypes;
}

export function ConditionBuilder({ onInsert, initialExpression, variableTypes }: ConditionBuilderProps): React.JSX.Element {
    const theme = useTheme();
    const focusedEditorContainerRef = useRef<HTMLElement | null>(null);

    const [contextFilter, setContextFilter] = useState("");

    const [combinator, setCombinator] = useState<Combinator>(() => {
        if (initialExpression) {
            const parsed = parseSpel(initialExpression);
            if (parsed) return parsed.combinator;
        }
        return "&&";
    });

    const [conditions, setConditions] = useState<Condition[]>(() => {
        if (initialExpression) {
            const parsed = parseSpel(initialExpression);
            if (parsed) return parsed.conditions.map((c) => makeCondition(c));
        }
        return [makeCondition()];
    });

    const [tooComplex] = useState(() => !!initialExpression && !parseSpel(initialExpression));

    const updateCondition = useCallback((id: number, key: keyof Condition, value: string) => {
        setConditions((prev) => prev.map((c) => (c.id !== id ? c : { ...c, [key]: value })));
    }, []);

    const removeCondition = useCallback((id: number) => {
        setConditions((prev) => {
            if (prev.length === 1) return [makeCondition()];
            return prev.filter((c) => c.id !== id);
        });
    }, []);

    const addCondition = useCallback(() => {
        setConditions((prev) => [...prev, makeCondition()]);
    }, []);

    const preview = useMemo(() => genSpel(conditions, combinator), [conditions, combinator]);

    // Context tree data from variableTypes
    const contextEntries = useMemo<[string, unknown][]>(() => {
        if (!variableTypes) return [];
        return Object.entries(variableTypes)
            .filter(([key]) => {
                if (!contextFilter) return true;
                return key.toLowerCase().includes(contextFilter.toLowerCase());
            })
            .map(([key, typingResult]) => {
                const sample = typingResultToSample(typingResult);
                return [`#${key}`, sample ?? null] as [string, unknown];
            });
    }, [variableTypes, contextFilter]);

    const handleTreeInsert = useCallback((text: string) => {
        const container = focusedEditorContainerRef.current;
        if (!container) return;
        const aceEl = container.querySelector(".ace_editor") as {
            env?: {
                editor?: { session: { insert: (pos: unknown, text: string) => void }; getCursorPosition: () => unknown; focus: () => void };
            };
        } | null;
        if (aceEl?.env?.editor) {
            aceEl.env.editor.session.insert(aceEl.env.editor.getCursorPosition(), text);
            aceEl.env.editor.focus();
        }
    }, []);

    return (
        <RootBox>
            <Box sx={{ flex: 1, overflowY: "auto", minHeight: 0, display: "flex", flexDirection: "column", gap: 2, p: 2.5 }}>
                <Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
                    {/* Left: Context Variables */}
                    <PanelPaper sx={{ width: 280, flexShrink: 0 }}>
                        <PanelHeader>
                            <Box>
                                <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Context Variables</Typography>
                                <Typography sx={{ fontSize: 11, color: "text.secondary" }}>Click or drag to insert</Typography>
                            </Box>
                        </PanelHeader>
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
                            {contextEntries.length === 0 ? (
                                <Typography sx={{ fontSize: 12, color: "text.disabled", px: 1, py: 1 }}>
                                    {variableTypes && Object.keys(variableTypes).length === 0 ? "No variables available" : "No matches"}
                                </Typography>
                            ) : (
                                contextEntries.map(([name, value]) => (
                                    <ContextTreeNode
                                        key={name}
                                        name={name}
                                        value={value}
                                        path={name}
                                        depth={0}
                                        onInsert={handleTreeInsert}
                                    />
                                ))
                            )}
                        </ScrollArea>
                    </PanelPaper>

                    {/* Right: Conditions */}
                    <Box sx={{ flex: 1, display: "flex", flexDirection: "column", gap: 1.5 }}>
                        {tooComplex && (
                            <Chip
                                icon={<WarningAmberIcon sx={{ fontSize: 14 }} />}
                                label="Expression too complex to visualize — editing will replace it"
                                size="small"
                                color="warning"
                                variant="outlined"
                                sx={{ alignSelf: "flex-start", fontSize: 11 }}
                            />
                        )}

                        {/* Combinator selector */}
                        {conditions.length > 1 && (
                            <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                                <Typography sx={{ fontSize: 12, color: "text.secondary" }}>Combine with:</Typography>
                                <Select
                                    size="small"
                                    value={combinator}
                                    onChange={(e) => setCombinator(e.target.value as Combinator)}
                                    sx={{ fontSize: 12, minWidth: 80 }}
                                >
                                    <MenuItem value="&&">AND (&&)</MenuItem>
                                    <MenuItem value="||">OR (||)</MenuItem>
                                </Select>
                            </Box>
                        )}

                        {/* Condition rows */}
                        {conditions.map((cond, idx) => (
                            <ConditionRow
                                key={cond.id}
                                condition={cond}
                                index={idx}
                                variableTypes={variableTypes ?? {}}
                                focusedEditorContainerRef={focusedEditorContainerRef}
                                onUpdate={updateCondition}
                                onRemove={removeCondition}
                                isLast={conditions.length === 1}
                            />
                        ))}

                        {/* Add condition */}
                        <Box>
                            <Button
                                size="small"
                                startIcon={<AddIcon sx={{ fontSize: 14 }} />}
                                onClick={addCondition}
                                sx={{ fontSize: 12, textTransform: "none", color: "text.secondary" }}
                            >
                                Add condition
                            </Button>
                        </Box>

                        {/* Expression preview */}
                        <PanelPaper sx={{ opacity: 0.65, "&:hover": { opacity: 1 }, transition: "opacity 0.2s" }}>
                            <PanelHeader>
                                <Typography sx={{ fontSize: 12, fontWeight: 500, color: "text.secondary" }}>Expression preview</Typography>
                            </PanelHeader>
                            <SpelOutput>{preview || <span style={{ color: theme.palette.text.disabled }}>{"(empty)"}</span>}</SpelOutput>
                        </PanelPaper>
                    </Box>
                </Box>
            </Box>

            {/* Footer */}
            <Box
                sx={{
                    borderTop: `1px solid ${theme.palette.divider}`,
                    px: 2.5,
                    py: 1,
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    flexShrink: 0,
                    backgroundColor: alpha(theme.palette.background.paper, 0.6),
                }}
            >
                <Typography sx={{ fontSize: 11, color: "text.secondary" }}>
                    Click a variable to insert at cursor, or drag it into a field
                </Typography>
                {onInsert && (
                    <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        onClick={() => onInsert(preview)}
                        sx={{ textTransform: "none" }}
                    >
                        Apply
                    </Button>
                )}
            </Box>
        </RootBox>
    );
}

// ─── ConditionRow ─────────────────────────────────────────────────────────────

interface ConditionRowProps {
    condition: Condition;
    index: number;
    variableTypes: VariableTypes;
    focusedEditorContainerRef: React.MutableRefObject<HTMLElement | null>;
    onUpdate: (id: number, key: keyof Condition, value: string) => void;
    onRemove: (id: number) => void;
    isLast: boolean;
}

function ConditionRow({
    condition,
    index,
    variableTypes,
    focusedEditorContainerRef,
    onUpdate,
    onRemove,
    isLast,
}: ConditionRowProps): React.JSX.Element {
    const theme = useTheme();
    const leftContainerRef = useRef<HTMLElement>(null);
    const rightContainerRef = useRef<HTMLElement>(null);
    const isNullOp = NULL_OPERATORS.has(condition.operator);

    return (
        <Box
            sx={{
                display: "flex",
                alignItems: "center",
                gap: 1,
                p: 1,
                border: `1px solid ${theme.palette.divider}`,
                borderRadius: 1,
                backgroundColor: alpha(theme.palette.background.paper, 0.4),
            }}
        >
            {/* Row index */}
            <Typography sx={{ fontSize: 11, color: "text.disabled", minWidth: 16, textAlign: "center", flexShrink: 0 }}>
                {index + 1}
            </Typography>

            {/* Left expression */}
            <SpelEditorContainer onDrop={(e) => clearAceSelectionAfterDrop(e.currentTarget)}>
                <Box
                    ref={leftContainerRef}
                    sx={{ flex: 1 }}
                    onFocus={() => {
                        focusedEditorContainerRef.current = leftContainerRef.current;
                    }}
                >
                    <ExpressionSuggest
                        inputProps={{
                            value: condition.left,
                            language: ExpressionLang.SpEL,
                            onValueChange: (v: string) => onUpdate(condition.id, "left", v),
                            rows: 1,
                            placeholder: "left operand",
                        }}
                        variableTypes={variableTypes}
                        fieldErrors={[]}
                    />
                </Box>
            </SpelEditorContainer>

            {/* Operator */}
            <Select
                size="small"
                value={condition.operator}
                onChange={(e) => onUpdate(condition.id, "operator", e.target.value)}
                sx={{ fontSize: 12, flexShrink: 0, minWidth: 160 }}
            >
                {OPERATORS.map((op) => (
                    <MenuItem key={op.value} value={op.value} sx={{ fontSize: 12 }}>
                        {op.label}
                    </MenuItem>
                ))}
            </Select>

            {/* Right expression (hidden for null operators) */}
            {!isNullOp ? (
                <SpelEditorContainer onDrop={(e) => clearAceSelectionAfterDrop(e.currentTarget)}>
                    <Box
                        ref={rightContainerRef}
                        sx={{ flex: 1 }}
                        onFocus={() => {
                            focusedEditorContainerRef.current = rightContainerRef.current;
                        }}
                    >
                        <ExpressionSuggest
                            inputProps={{
                                value: condition.right,
                                language: ExpressionLang.SpEL,
                                onValueChange: (v: string) => onUpdate(condition.id, "right", v),
                                rows: 1,
                                placeholder: "right operand",
                            }}
                            variableTypes={variableTypes}
                            fieldErrors={[]}
                        />
                    </Box>
                </SpelEditorContainer>
            ) : (
                <Box sx={{ flex: 1 }} />
            )}

            {/* Remove */}
            <Tooltip title={isLast ? "Clear condition" : "Remove condition"}>
                <IconButton size="small" onClick={() => onRemove(condition.id)} sx={{ flexShrink: 0 }}>
                    <DeleteIcon sx={{ fontSize: 16 }} />
                </IconButton>
            </Tooltip>
        </Box>
    );
}
