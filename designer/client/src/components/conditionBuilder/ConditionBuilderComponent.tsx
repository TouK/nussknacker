import { Dialog, DialogContent } from "@mui/material";
import React, { useMemo, useState } from "react";

import { useUserSettings } from "../../common/useUserSettings";
import type { NodeType } from "../../types/node";
import type { VariableTypes } from "../../types/validation";
import type { ContextData } from "../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../dataMapper/DataMapperDialogTitle";
import { useInputOutputContext } from "../graph/node-modal/io/InputOutputContext";
import { BuilderIconButton } from "../graph/node-modal/node-action-buttons/StyledLoadingButton";
import { ConditionBuilder } from "./ConditionBuilder";

interface Props {
    node: NodeType;
    onInsert: (spel: string) => void;
    initialExpression?: string;
    variableTypes?: VariableTypes;
    highlightKeys?: Set<string>;
    /** Override context data; if omitted, derived from ioContext. */
    contextData?: ContextData;
    /** Custom trigger button; if omitted, renders StyledLoadingButton. */
    renderTrigger?: (onClick: () => void) => React.ReactNode;
}

export function ConditionBuilderComponent({
    node,
    onInsert,
    initialExpression,
    variableTypes,
    highlightKeys,
    contextData: contextDataProp,
    renderTrigger,
}: Props): React.JSX.Element | null {
    const [showConditionBuilder] = useUserSettings("node.showConditionBuilder");
    const [open, setOpen] = useState(false);

    const ioContext = useInputOutputContext();
    const ioContextData = useMemo<ContextData | undefined>(() => {
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        if (!contexts.length) return undefined;
        const selected = ioContext?.state.inputDataSetId ? contexts.find((c) => c.id === ioContext.state.inputDataSetId) : undefined;
        const vars = (selected ?? contexts[0]).variables;
        return Object.fromEntries(Object.keys(vars).map((k) => [k, vars[k].pretty]));
    }, [ioContext]);

    const contextData = contextDataProp ?? ioContextData;

    if (!showConditionBuilder) return null;

    return (
        <>
            {renderTrigger ? renderTrigger(() => setOpen(true)) : <BuilderIconButton onClick={() => setOpen(true)} />}
            {open && (
                <Dialog open onClose={() => setOpen(false)} maxWidth="xl" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={() => setOpen(false)} title="condition builder" />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <ConditionBuilder
                            onInsert={(spel) => {
                                onInsert(spel);
                                setOpen(false);
                            }}
                            variableTypes={variableTypes}
                            contextData={contextData}
                            initialExpression={initialExpression}
                            highlightKeys={highlightKeys}
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
