import { Box, Dialog, DialogContent } from "@mui/material";
import React, { useState } from "react";


import type { NodeType } from "../../types/node";
import type { VariableTypes } from "../../types/validation";
import { DataMapperDialogTitle } from "../dataMapper/DataMapperDialogTitle";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import { StyledLoadingButton } from "../graph/node-modal/node-action-buttons/StyledLoadingButton";
import { ConditionBuilder } from "./ConditionBuilder";

interface Props {
    node: NodeType;
    variableTypes?: VariableTypes;
    onInsert: (spel: string) => void;
    expression: { language: string; expression: string } | undefined;
}

export function FilterConditionBuilder({ node, variableTypes, onInsert, expression }: Props): React.JSX.Element {
    const [open, setOpen] = useState(false);

    const handleInsert = (spel: string) => {
        onInsert(spel);
        setOpen(false);
    };

    return (
        <>
            <Box display="flex" flexDirection="column" alignItems="flex-end" width="100%">
                <StyledLoadingButton title="Condition Builder" action={() => setOpen(true)} />
            </Box>
            {open && (
                <Dialog open onClose={() => setOpen(false)} maxWidth="lg" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={() => setOpen(false)} title="condition builder" />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <ConditionBuilder
                            onInsert={handleInsert}
                            variableTypes={variableTypes}
                            initialExpression={expression?.language === ExpressionLang.SpEL ? expression.expression : undefined}
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
