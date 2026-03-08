import AccountTreeIcon from "@mui/icons-material/AccountTree";
import { Dialog, DialogContent, IconButton, Tooltip } from "@mui/material";
import React, { useCallback, useMemo, useState } from "react";

import { useAppSelector } from "../../../store/storeHelpers";
import type { NodeType } from "../../../types/node";
import { DataMapper } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import { ExpressionLang } from "./editors/expression/types";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface Props {
    node: NodeType;
    valuePath: string;
    setProperty: SetProperty;
}

function getBodyExpression(node: NodeType): string | undefined {
    if (node.type === "Sink" || node.type === "Source") {
        return (node.ref as { parameters: Array<{ name: string; expression: { language: string; expression: string } }> }).parameters?.find(
            (p) => p.name === "Body",
        )?.expression?.expression;
    }
    return (
        node as unknown as { service: { parameters: Array<{ name: string; expression: { language: string; expression: string } }> } }
    ).service?.parameters?.find((p) => p.name === "Body")?.expression?.expression;
}

export function HttpBodyDataMapper({ node, valuePath, setProperty }: Props): React.JSX.Element {
    const [open, setOpen] = useState(false);
    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => {
        const vars = findAvailableVariables(node.id);
        // For Enricher nodes the output variable is produced by this node itself —
        // it must not appear as an available input variable in its own expressions.
        const outputVar: string | undefined = node.type === "Enricher" ? node.output : undefined;
        if (outputVar && outputVar in vars) {
            const { [outputVar]: _omit, ...rest } = vars;
            return rest;
        }
        return vars;
    }, [findAvailableVariables, node]);

    const handleInsert = useCallback(
        (spel: string) => {
            setProperty(valuePath, { expression: spel, language: ExpressionLang.SpEL });
            setOpen(false);
        },
        [setProperty, valuePath],
    );

    return (
        <>
            <Tooltip title="Open Data Mapper">
                <IconButton size="small" onClick={() => setOpen(true)} sx={{ p: "2px" }}>
                    <AccountTreeIcon sx={{ fontSize: 16 }} />
                </IconButton>
            </Tooltip>
            {open && (
                <Dialog open onClose={() => setOpen(false)} maxWidth="xl" fullWidth>
                    <DataMapperDialogTitle node={node} onClose={() => setOpen(false)} />
                    <DialogContent sx={{ p: 0, display: "flex", flexDirection: "column", overflow: "hidden" }}>
                        <DataMapper onInsert={handleInsert} variableTypes={variableTypes} initialExpression={getBodyExpression(node)} />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
