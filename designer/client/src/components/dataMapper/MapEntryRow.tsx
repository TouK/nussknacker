import DeleteIcon from "@mui/icons-material/Delete";
import { Box, IconButton, TextField, Typography } from "@mui/material";
import React, { useCallback, useRef } from "react";

import type { VariableTypes } from "../../types/validation";
import { clearAceSelectionAfterDrop } from "../builderComponents/aceUtils";
import { SpelEditorContainer } from "../builderComponents/panelStyles";
import { ExpressionSuggest } from "../graph/node-modal/editors/expression/ExpressionSuggest";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import type { MapEntryDef } from "./dataMapperUtils";

// ─── Props ────────────────────────────────────────────────────────────────────

interface MapEntryRowProps {
    entry: MapEntryDef;
    variableTypes: VariableTypes;
    onChange: (key: keyof MapEntryDef, value: unknown) => void;
    onRemove: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function MapEntryRow({ entry, variableTypes, onChange, onRemove }: MapEntryRowProps): React.JSX.Element {
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
