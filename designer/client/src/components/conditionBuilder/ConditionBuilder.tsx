import AddIcon from "@mui/icons-material/Add";
import SearchIcon from "@mui/icons-material/Search";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import { alpha, Box, Button, Chip, InputAdornment, MenuItem, Select, styled, TextField, Typography, useTheme } from "@mui/material";
import React, { useCallback, useMemo, useRef, useState } from "react";

import type { VariableTypes } from "../../types/validation";
import { ContextTreeNode } from "../builderComponents/ContextTreeNode";
import { PanelHeader, PanelPaper, ScrollArea, SpelOutput } from "../builderComponents/panelStyles";
import { typingResultToSample } from "../builderComponents/typeUtils";
import { ConditionRow } from "./ConditionRow";
import type { Combinator, Condition } from "./spelUtils";
import { genSpel, makeCondition, parseSpel } from "./spelUtils";

// ─── Styled ───────────────────────────────────────────────────────────────────

const RootBox = styled(Box)(({ theme }) => ({
    display: "flex",
    flexDirection: "column",
    flex: 1,
    minHeight: 0,
    overflow: "hidden",
    height: "100%",
    backgroundColor: theme.palette.background.default,
}));

// ─── Props ────────────────────────────────────────────────────────────────────

export interface ConditionBuilderProps {
    onInsert?: (spel: string) => void;
    initialExpression?: string;
    variableTypes?: VariableTypes;
}

// ─── ConditionBuilder ─────────────────────────────────────────────────────────

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
                        <ScrollArea sx={{ p: 1, pt: 0.5, maxHeight: 480 }}>
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
                                        onSelect={handleTreeInsert}
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

                        {/* Conditions panel */}
                        <PanelPaper>
                            <PanelHeader>
                                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}>
                                    <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Conditions</Typography>
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
                                </Box>
                            </PanelHeader>
                            <Box sx={{ display: "flex", flexDirection: "column", gap: 1, p: 1 }}>
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
                            </Box>
                        </PanelPaper>

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
