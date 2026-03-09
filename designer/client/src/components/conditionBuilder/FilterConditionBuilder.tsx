import AccountTreeIcon from "@mui/icons-material/AccountTree";
import { Dialog, DialogContent, IconButton, Tooltip } from "@mui/material";
import React, { useState } from "react";

import type { NodeType } from "../../types/node";
import type { VariableTypes } from "../../types/validation";
import { DataMapperDialogTitle } from "../dataMapper/DataMapperDialogTitle";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import type { SetProperty } from "../graph/node-modal/useNodeTypeDetailsContentLogic";
import { ConditionBuilder } from "./ConditionBuilder";

interface Props {
    node: NodeType;
    variableTypes?: VariableTypes;
    setProperty: SetProperty;
    expression: { language: string; expression: string } | undefined;
}

export function FilterConditionBuilder({ node, variableTypes, setProperty, expression }: Props): React.JSX.Element {
    const [open, setOpen] = useState(false);

    const handleInsert = (spel: string) => {
        setProperty("expression", { expression: spel, language: ExpressionLang.SpEL });
        setOpen(false);
    };

    return (
        <>
            <Tooltip title="Open Condition Builder">
                <IconButton size="small" onClick={() => setOpen(true)} sx={{ p: "2px" }}>
                    <AccountTreeIcon sx={{ fontSize: 16 }} />
                </IconButton>
            </Tooltip>
            {open && (
                <Dialog open onClose={() => setOpen(false)} maxWidth="lg" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={() => setOpen(false)} />
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
