import DeleteIcon from "@mui/icons-material/Delete";
import { alpha, Box, IconButton, MenuItem, Select, TextField, Tooltip, Typography, useTheme } from "@mui/material";
import React, { useEffect, useRef } from "react";

import type { VariableTypes } from "../../types/validation";
import { clearAceSelectionAfterDrop } from "../builderComponents/aceUtils";
import { SpelEditorContainer } from "../builderComponents/panelStyles";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import type { FieldError } from "../graph/node-modal/editors/Validators";
import type { Condition } from "./spelUtils";
import { NO_RHS_OPERATORS, OPERATORS, REGEX_OPERATORS } from "./spelUtils";

// ─── Props ────────────────────────────────────────────────────────────────────

interface ConditionRowProps {
    condition: Condition;
    index: number;
    variableTypes: VariableTypes;
    focusedEditorContainerRef: React.MutableRefObject<HTMLElement | null>;
    onUpdate: (id: number, key: keyof Condition, value: string) => void;
    onRemove: (id: number) => void;
    isLast: boolean;
    leftErrors: FieldError[];
    rightErrors: FieldError[];
    onValidate: (side: "left" | "right", expression: string) => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function ConditionRow({
    condition,
    index,
    variableTypes,
    focusedEditorContainerRef,
    onUpdate,
    onRemove,
    isLast,
    leftErrors,
    rightErrors,
    onValidate,
}: ConditionRowProps): React.JSX.Element {
    const theme = useTheme();
    const leftContainerRef = useRef<HTMLElement>(null);
    const rightContainerRef = useRef<HTMLElement>(null);
    const isNullOp = NO_RHS_OPERATORS.has(condition.operator);
    const isRegexOp = REGEX_OPERATORS.has(condition.operator);

    const onValidateRef = useRef(onValidate);
    onValidateRef.current = onValidate;

    useEffect(() => {
        const timer = setTimeout(() => onValidateRef.current("left", condition.left), 500);
        return () => clearTimeout(timer);
    }, [condition.left]);

    useEffect(() => {
        if (isNullOp) return;
        const timer = setTimeout(() => onValidateRef.current("right", condition.right), 500);
        return () => clearTimeout(timer);
    }, [condition.right, isNullOp]);

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
                    onBlur={() => {
                        if (focusedEditorContainerRef.current === leftContainerRef.current) focusedEditorContainerRef.current = null;
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
                        showValidation
                        variableTypes={variableTypes}
                        fieldErrors={leftErrors}
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

            {/* Right expression */}
            {isNullOp ? (
                <Box sx={{ flex: 1 }} />
            ) : isRegexOp ? (
                <TextField
                    size="small"
                    value={condition.right}
                    onChange={(e) => onUpdate(condition.id, "right", e.target.value)}
                    placeholder="regex pattern, e.g. [A-Z]{3}.*"
                    sx={{ flex: 1, "& .MuiInputBase-input": { fontSize: 12, fontFamily: "monospace", py: "5px" } }}
                />
            ) : (
                <SpelEditorContainer onDrop={(e) => clearAceSelectionAfterDrop(e.currentTarget)}>
                    <Box
                        ref={rightContainerRef}
                        sx={{ flex: 1 }}
                        onFocus={() => {
                            focusedEditorContainerRef.current = rightContainerRef.current;
                        }}
                        onBlur={() => {
                            if (focusedEditorContainerRef.current === rightContainerRef.current) focusedEditorContainerRef.current = null;
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
                            showValidation
                            variableTypes={variableTypes}
                            fieldErrors={rightErrors}
                        />
                    </Box>
                </SpelEditorContainer>
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
