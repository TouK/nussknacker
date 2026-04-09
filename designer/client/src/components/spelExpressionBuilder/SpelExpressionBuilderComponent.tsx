import { Dialog, DialogContent } from "@mui/material";
import React, { useMemo, useState } from "react";

import { useUserSettings } from "../../common/useUserSettings";
import { useAppSelector } from "../../store/storeHelpers";
import type { NodeType } from "../../types/node";
import type { VariableTypes } from "../../types/validation";
import type { ContextData } from "../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../dataMapper/DataMapperDialogTitle";
import { useInputOutputContext } from "../graph/node-modal/io/InputOutputContext";
import { getFindAvailableVariables } from "../graph/node-modal/NodeDetailsContent/selectors";
import { SpelExpressionBuilder } from "./SpelExpressionBuilder";

// ─── Props ────────────────────────────────────────────────────────────────────

interface Props {
    node: NodeType;
    onInsert: (spel: string) => void;
    initialExpression?: string;
    paramName?: string;
    targetTypeDisplay?: string;
    /** Override variable types instead of deriving from node id. */
    variableTypes?: VariableTypes;
    /** Override context data (actual values) instead of deriving from InputOutputContext. */
    contextData?: ContextData;
    /** Controlled mode: open state managed externally. */
    open?: boolean;
    onClose?: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function SpelExpressionBuilderComponent({
    node,
    onInsert,
    initialExpression,
    paramName,
    targetTypeDisplay,
    variableTypes: variableTypesProp,
    contextData: contextDataProp,
    open: openProp,
    onClose,
}: Props): React.JSX.Element | null {
    const [showDataMapper] = useUserSettings("node.showFieldExpressionBuilder");
    const [openInternal, setOpenInternal] = useState(false);
    const isControlled = openProp !== undefined;
    const open = isControlled ? openProp : openInternal;

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypesFromNode = useMemo(() => findAvailableVariables(node.id), [findAvailableVariables, node.id]);
    const variableTypes = variableTypesProp ?? variableTypesFromNode;

    const buildProbeNode = useMemo(() => {
        if (!paramName) return undefined;
        return (expr: string) => ({
            ...node,
            parameters: (node.parameters ?? []).map((p) =>
                p.name === paramName ? { ...p, expression: { language: "spel", expression: expr } } : p,
            ),
        });
    }, [node, paramName]);

    const ioContext = useInputOutputContext();
    const contextDataFromIo = useMemo<ContextData | undefined>(() => {
        if (contextDataProp !== undefined) return undefined;
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        if (!contexts.length) return undefined;
        const selected = ioContext?.state.inputDataSetId ? contexts.find((c) => c.id === ioContext.state.inputDataSetId) : undefined;
        const vars = (selected ?? contexts[0]).variables;
        return Object.fromEntries(Object.keys(vars).map((k) => [k, vars[k].pretty]));
    }, [ioContext, contextDataProp]);
    const contextData = contextDataProp ?? contextDataFromIo;

    if (!showDataMapper) return null;

    const handleClose = () => (isControlled ? onClose?.() : setOpenInternal(false));

    return (
        <>
            {open && (
                <Dialog open onClose={handleClose} maxWidth="xl" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={handleClose} title="expression builder" />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <SpelExpressionBuilder
                            onInsert={(spel) => {
                                onInsert(spel);
                                handleClose();
                            }}
                            variableTypes={variableTypes}
                            contextData={contextData}
                            initialExpression={initialExpression}
                            buildProbeNode={buildProbeNode}
                            targetTypeDisplay={targetTypeDisplay}
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
