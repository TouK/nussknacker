import { Box, Dialog, DialogContent } from "@mui/material";
import React, { useMemo, useState } from "react";

import type { NodeType } from "../../types/node";
import type { VariableTypes } from "../../types/validation";
import type { ContextData } from "../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../dataMapper/DataMapperDialogTitle";
import { ExpressionLang } from "../graph/node-modal/editors/expression/types";
import { useInputOutputContext } from "../graph/node-modal/io/InputOutputContext";
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

    const ioContext = useInputOutputContext();
    const contextData = useMemo<ContextData | undefined>(() => {
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        if (!contexts.length) return undefined;
        const vars = contexts[0].variables;
        return Object.fromEntries(Object.keys(vars).map((k) => [k, vars[k].pretty]));
    }, [ioContext]);

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
                            contextData={contextData}
                            initialExpression={expression?.language === ExpressionLang.SpEL ? expression.expression : undefined}
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
