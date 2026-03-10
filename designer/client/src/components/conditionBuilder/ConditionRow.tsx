import DeleteIcon from "@mui/icons-material/Delete";
import { alpha, Box, IconButton, MenuItem, Select, Tooltip, Typography, useTheme } from "@mui/material";
import React, { useRef } from "react";

import type { VariableTypes } from "../../types/validation";
import { clearAceSelectionAfterDrop } from "../builderComponents/aceUtils";
import { SpelEditorContainer } from "../builderComponents/panelStyles";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import type { Condition } from "./spelUtils";
import { NO_RHS_OPERATORS, OPERATORS } from "./spelUtils";

// ─── Props ────────────────────────────────────────────────────────────────────

interface ConditionRowProps {
    condition: Condition;
    index: number;
    variableTypes: VariableTypes;
    focusedEditorContainerRef: React.MutableRefObject<HTMLElement | null>;
    onUpdate: (id: number, key: keyof Condition, value: string) => void;
    onRemove: (id: number) => void;
    isLast: boolean;
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
}: ConditionRowProps): React.JSX.Element {
    const theme = useTheme();
    const leftContainerRef = useRef<HTMLElement>(null);
    const rightContainerRef = useRef<HTMLElement>(null);
    const isNullOp = NO_RHS_OPERATORS.has(condition.operator);

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
