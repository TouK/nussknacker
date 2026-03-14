import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import SearchIcon from "@mui/icons-material/Search";
import {
    alpha,
    Box,
    Button,
    FormControlLabel,
    IconButton,
    InputAdornment,
    Switch,
    TextField,
    Tooltip,
    Typography,
    useTheme,
} from "@mui/material";
import React, { useCallback, useEffect, useRef, useState } from "react";

import HttpService from "../../http/HttpService/instance";
import { useAppSelector } from "../../store/storeHelpers";
import type { VariableTypes } from "../../types/validation";
import { clearAceSelectionAfterDrop } from "../builderComponents/aceUtils";
import { ContextTreeNode } from "../builderComponents/ContextTreeNode";
import { PanelHeader, PanelPaper, ScrollArea, SpelEditorContainer, SpelOutput } from "../builderComponents/panelStyles";
import { typingResultToSample } from "../builderComponents/typeUtils";
import type { ContextData } from "../dataMapper/DataMapper";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import type { FieldError } from "../graph/node-modal/editors/Validators";
import { getProcessName, getProcessProperties } from "../graph/node-modal/NodeDetailsContent/selectors";

// ─── Props ────────────────────────────────────────────────────────────────────

export interface SpelExpressionPickerProps {
    onInsert?: (spel: string) => void;
    initialExpression?: string;
    variableTypes?: VariableTypes;
    contextData?: ContextData;
    /** Factory that builds the probe node for validation; if omitted, no type-checking is performed. */
    buildProbeNode?: (expr: string) => unknown;
    /** Human-readable target type shown as a hint, e.g. "Collection[Unknown]". */
    targetTypeDisplay?: string;
}

// ─── Path resolver ────────────────────────────────────────────────────────────

function resolveSpelPath(expression: string, contextData: ContextData): unknown {
    if (!expression.startsWith("#")) return undefined;
    let str = expression.slice(1);
    const tokens: string[] = [];
    while (str.length > 0) {
        if (str.startsWith("?.")) {
            str = str.slice(2);
        } else if (str.startsWith(".")) {
            str = str.slice(1);
        } else if (str.startsWith("['") || str.startsWith('["')) {
            const close = str.indexOf(str[1] === "'" ? "']" : '"]');
            if (close === -1) break;
            tokens.push(str.slice(2, close));
            str = str.slice(close + 2);
        } else if (str.startsWith("[")) {
            const close = str.indexOf("]");
            if (close === -1) break;
            tokens.push(str.slice(1, close));
            str = str.slice(close + 1);
        } else {
            const match = str.match(/^[a-zA-Z_$][a-zA-Z0-9_$]*/);
            if (!match) break;
            tokens.push(match[0]);
            str = str.slice(match[0].length);
        }
    }
    let current: unknown = contextData;
    for (const token of tokens) {
        if (current == null || typeof current !== "object") return undefined;
        current = (current as Record<string, unknown>)[token];
    }
    return current;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function SpelExpressionPicker({
    onInsert,
    initialExpression,
    variableTypes,
    contextData,
    buildProbeNode,
    targetTypeDisplay,
}: SpelExpressionPickerProps): React.JSX.Element {
    const theme = useTheme();
    const processName = useAppSelector(getProcessName);
    const processProperties = useAppSelector(getProcessProperties);

    const [expression, setExpression] = useState(initialExpression ?? "");
    const [errors, setErrors] = useState<FieldError[]>([]);
    const [contextFilter, setContextFilter] = useState("");
    const [selectedPath, setSelectedPath] = useState<string | null>(null);
    const [nullSafe, setNullSafe] = useState(true);

    const resolvedValue = React.useMemo(
        () => (contextData && expression ? resolveSpelPath(expression, contextData) : undefined),
        [contextData, expression],
    );
    const resolvedItems = Array.isArray(resolvedValue) ? (resolvedValue as unknown[]) : undefined;

    const validateFn = useCallback(
        async (expr: string) => {
            if (!processName || !processProperties || !expr.trim() || !buildProbeNode) {
                setErrors([]);
                return;
            }
            const result = await HttpService.validateNode(processName, {
                nodeData: buildProbeNode(expr) as never,
                variableTypes: variableTypes ?? {},
                branchVariableTypes: {},
                outgoingEdges: [],
                testCases: {},
                processProperties,
            });
            if (!result) return;
            setErrors(result.validationErrors.map(({ message, description, details }) => ({ message, description, details })));
        },
        [processName, processProperties, variableTypes, buildProbeNode],
    );

    const validateFnRef = useRef(validateFn);
    validateFnRef.current = validateFn;

    useEffect(() => {
        const timer = setTimeout(() => validateFnRef.current(expression), 500);
        return () => clearTimeout(timer);
    }, [expression]);

    const contextEntries = Object.entries(variableTypes ?? {}).filter(
        ([key]) => !contextFilter || key.toLowerCase().includes(contextFilter.toLowerCase()),
    );

    return (
        <Box
            sx={{
                display: "flex",
                flexDirection: "column",
                flex: 1,
                minHeight: 0,
                overflow: "hidden",
                height: "100%",
                backgroundColor: theme.palette.background.default,
            }}
        >
            <Box sx={{ flex: 1, overflowY: "auto", minHeight: 0, display: "flex", flexDirection: "column", gap: 2, p: 2.5 }}>
                <Box sx={{ display: "flex", gap: 2, alignItems: "flex-start" }}>
                    {/* Left: Context Variables */}
                    <PanelPaper sx={{ width: 360, flexShrink: 0 }}>
                        <PanelHeader>
                            <Box>
                                <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Context Variables</Typography>
                                <Typography sx={{ fontSize: 11, color: "text.secondary" }}>Drag onto the expression field</Typography>
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
                                <Typography sx={{ fontSize: 12, color: "text.disabled", px: 1, py: 1 }}>No variables available</Typography>
                            ) : (
                                contextEntries.map(([key, typingResult]) => {
                                    const value = contextData?.[key] !== undefined ? contextData[key] : typingResultToSample(typingResult);
                                    return (
                                        <ContextTreeNode
                                            key={key}
                                            name={`#${key}`}
                                            value={value ?? null}
                                            path={`#${key}`}
                                            depth={0}
                                            onSelect={setSelectedPath}
                                            selectedPath={selectedPath}
                                            typing={typingResult}
                                            useTypedPaths
                                            nullSafe={nullSafe}
                                        />
                                    );
                                })
                            )}
                        </ScrollArea>
                    </PanelPaper>

                    {/* Right: SpEL Expression editor + Preview */}
                    <Box sx={{ flex: 1, display: "flex", flexDirection: "column", gap: 1.5 }}>
                        <PanelPaper>
                            <PanelHeader>
                                <Typography sx={{ fontSize: 13, fontWeight: 600 }}>Target Expression</Typography>
                                <Tooltip title="Use null-safe navigation (?.) when inserting paths from context variables (drag & drop)">
                                    <FormControlLabel
                                        control={<Switch size="small" checked={nullSafe} onChange={(e) => setNullSafe(e.target.checked)} />}
                                        label={
                                            <Typography sx={{ fontSize: 11, color: "text.secondary", userSelect: "none" }}>
                                                Null-safe
                                            </Typography>
                                        }
                                        sx={{ mr: 0.5 }}
                                    />
                                </Tooltip>
                            </PanelHeader>
                            <Box sx={{ p: 1 }}>
                                <Box sx={{ position: "relative" }}>
                                    <SpelEditorContainer onDrop={(e) => clearAceSelectionAfterDrop(e.currentTarget)}>
                                        <ExpressionSuggest
                                            inputProps={{
                                                value: expression,
                                                language: ExpressionLang.SpEL,
                                                onValueChange: setExpression,
                                                rows: 4,
                                                placeholder: "SpEL expression, e.g. #http_output?.response?.body['states']",
                                            }}
                                            showValidation
                                            variableTypes={variableTypes ?? {}}
                                            fieldErrors={errors}
                                        />
                                    </SpelEditorContainer>
                                    {expression && (
                                        <Tooltip title="Clear expression">
                                            <IconButton
                                                size="small"
                                                onClick={() => {
                                                    setExpression("");
                                                    setErrors([]);
                                                }}
                                                sx={{ position: "absolute", top: 4, right: 4, opacity: 0.5, "&:hover": { opacity: 1 } }}
                                            >
                                                <DeleteOutlineIcon sx={{ fontSize: 20 }} />
                                            </IconButton>
                                        </Tooltip>
                                    )}
                                </Box>
                            </Box>
                        </PanelPaper>

                        {/* Preview */}
                        <PanelPaper
                            sx={{ opacity: 0.65, "&:hover": { opacity: 1 }, transition: "opacity 0.2s", backgroundColor: "transparent" }}
                        >
                            <PanelHeader>
                                <Typography sx={{ fontSize: 12, fontWeight: 500, color: "text.secondary" }}>Preview</Typography>
                                <Tooltip title="Copy to clipboard">
                                    <IconButton size="small" onClick={() => navigator.clipboard?.writeText(expression)}>
                                        <ContentCopyIcon sx={{ fontSize: 16 }} />
                                    </IconButton>
                                </Tooltip>
                            </PanelHeader>
                            <SpelOutput>
                                {expression || <span style={{ color: theme.palette.text.disabled }}>(empty)</span>}
                                {resolvedItems && resolvedItems.length > 0 ? (
                                    <>
                                        {"\n"}
                                        {resolvedItems.map((item, i) => (
                                            <span key={i} style={{ color: theme.palette.text.secondary }}>
                                                {`→ ${JSON.stringify(item)}\n`}
                                            </span>
                                        ))}
                                    </>
                                ) : (
                                    expression && (
                                        <span style={{ color: theme.palette.text.disabled }}>
                                            {"\n"}
                                            {"To view preview, run a test or use live data"}
                                        </span>
                                    )
                                )}
                            </SpelOutput>
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
                    gap: 3,
                    flexShrink: 0,
                    backgroundColor: alpha(theme.palette.background.paper, 0.6),
                }}
            >
                {targetTypeDisplay && (
                    <Typography variant="caption" color="text.secondary">
                        Expected type:{" "}
                        <Box component="span" sx={{ fontFamily: "monospace" }}>
                            {targetTypeDisplay}
                        </Box>{" "}
                        — select a collection or wrap a value:{" "}
                        <Box component="span" sx={{ fontFamily: "monospace" }}>
                            {"{ #variable }"}
                        </Box>
                    </Typography>
                )}
                <Box sx={{ flex: 1 }} />
                {onInsert && (
                    <Button
                        variant="contained"
                        color="primary"
                        size="small"
                        onClick={() => onInsert(expression)}
                        sx={{ textTransform: "none" }}
                    >
                        Apply
                    </Button>
                )}
            </Box>
        </Box>
    );
}
