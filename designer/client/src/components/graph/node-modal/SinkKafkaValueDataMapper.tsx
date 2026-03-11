import { Box, Dialog, DialogContent } from "@mui/material";
import React, { useCallback, useMemo, useState } from "react";

import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import type { ContextData } from "../../dataMapper/DataMapper";
import { DataMapper } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import { ExpressionLang } from "./editors/expression/types";
import { useInputOutputContext } from "./io/InputOutputContext";
import { StyledLoadingButton } from "./node-action-buttons/StyledLoadingButton";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface Props {
    node: NodeType;
    valuePath: string;
    setProperty: SetProperty;
}

export function SinkKafkaValueDataMapper({ node, valuePath, setProperty }: Props): React.JSX.Element {
    const [open, setOpen] = useState(false);
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

    const handleInsert = useCallback(
        (spel: string) => {
            setProperty(valuePath, { expression: spel, language: ExpressionLang.SpEL });
            setOpen(false);
        },
        [setProperty, valuePath],
    );

    return (
        <>
            <Box display="flex" flexDirection="column" alignItems="flex-end" width="100%">
                <StyledLoadingButton title="Data Mapper" action={() => setOpen(true)} />
            </Box>
            {open && (
                <Dialog open onClose={() => setOpen(false)} maxWidth="xl" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={() => setOpen(false)} />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <DataMapper
                            onInsert={handleInsert}
                            variableTypes={variableTypes}
                            initialContext={initialContext}
                            initialExpression={
                                (
                                    node.ref as {
                                        parameters: Array<{ name: string; expression: { language: string; expression: string } }>;
                                    }
                                ).parameters.find((p) => p.name === "Value")?.expression?.expression
                            }
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
