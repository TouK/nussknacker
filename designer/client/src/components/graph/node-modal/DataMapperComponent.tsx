import { Dialog, DialogContent } from "@mui/material";
import React, { useMemo, useState } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import type { ContextData } from "../../dataMapper/DataMapper";
import { DataMapper } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import type { FieldDef } from "../../dataMapper/dataMapperUtils";
import { useInputOutputContext } from "./io/InputOutputContext";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";

interface Props {
    node: NodeType;
    onInsert: (spel: string) => void;
    initialExpression?: string;
    initialFields?: FieldDef[];
    hideFieldControls?: boolean;
    /** Controlled mode: open state managed externally. */
    open?: boolean;
    onClose?: () => void;
}

export function DataMapperComponent({
    node,
    onInsert,
    initialExpression,
    initialFields,
    hideFieldControls,
    open: openProp,
    onClose,
}: Props): React.JSX.Element | null {
    const [showDataMapper] = useUserSettings("node.showFieldExpressionBuilder");
    const [openInternal, setOpenInternal] = useState(false);
    const isControlled = openProp !== undefined;
    const open = isControlled ? openProp : openInternal;

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables(node.id), [findAvailableVariables, node.id]);

    const ioContext = useInputOutputContext();
    const initialContext = useMemo<ContextData | undefined>(() => {
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
                    <DataMapperDialogTitle node={node} onClose={handleClose} />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <DataMapper
                            onInsert={(spel) => {
                                onInsert(spel);
                                handleClose();
                            }}
                            variableTypes={variableTypes}
                            initialContext={initialContext}
                            initialExpression={initialExpression}
                            initialFields={initialFields}
                            hideFieldControls={hideFieldControls}
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
