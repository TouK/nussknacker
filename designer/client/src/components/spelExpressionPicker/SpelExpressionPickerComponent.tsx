import { Dialog, DialogContent } from "@mui/material";
import React, { useMemo, useState } from "react";

import { useUserSettings } from "../../common/useUserSettings";
import { useAppSelector } from "../../store/storeHelpers";
import type { NodeType } from "../../types/node";
import type { ContextData } from "../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../dataMapper/DataMapperDialogTitle";
import { useInputOutputContext } from "../graph/node-modal/io/InputOutputContext";
import { getFindAvailableVariables } from "../graph/node-modal/NodeDetailsContent/selectors";
import { SpelExpressionPicker } from "./SpelExpressionPicker";

// ─── Props ────────────────────────────────────────────────────────────────────

interface Props {
    node: NodeType;
    onInsert: (spel: string) => void;
    initialExpression?: string;
    paramName?: string;
    targetTypeDisplay?: string;
    /** Controlled mode: open state managed externally. */
    open?: boolean;
    onClose?: () => void;
}

// ─── Component ────────────────────────────────────────────────────────────────

export function SpelExpressionPickerComponent({
    node,
    onInsert,
    initialExpression,
    paramName,
    targetTypeDisplay,
    open: openProp,
    onClose,
}: Props): React.JSX.Element | null {
    const [showDataMapper] = useUserSettings("node.showFieldExpressionBuilder");
    const [openInternal, setOpenInternal] = useState(false);
    const isControlled = openProp !== undefined;
    const open = isControlled ? openProp : openInternal;

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables(node.id), [findAvailableVariables, node.id]);

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
    const contextData = useMemo<ContextData | undefined>(() => {
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        if (!contexts.length) return undefined;
        const selected = ioContext?.state.inputDataSetId ? contexts.find((c) => c.id === ioContext.state.inputDataSetId) : undefined;
        const vars = (selected ?? contexts[0]).variables;
        return Object.fromEntries(Object.keys(vars).map((k) => [k, vars[k].pretty]));
    }, [ioContext]);

    if (!showDataMapper) return null;

    const handleClose = () => (isControlled ? onClose?.() : setOpenInternal(false));

    return (
        <>
            {open && (
                <Dialog open onClose={handleClose} maxWidth="xl" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={handleClose} title="expression picker" />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <SpelExpressionPicker
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
