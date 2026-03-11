import { Box, Dialog, DialogContent } from "@mui/material";
import React, { useCallback, useMemo, useState } from "react";

import { useUserSettings } from "../../../common/useUserSettings";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import type { ContextData } from "../../dataMapper/DataMapper";
import { DataMapper } from "../../dataMapper/DataMapper";
import { DataMapperDialogTitle } from "../../dataMapper/DataMapperDialogTitle";
import { makeField, parseSpelToFields, refClazzToNuType } from "../../dataMapper/dataMapperUtils";
import type { FieldDef } from "../../dataMapper/dataMapperUtils";
import { ExpressionLang } from "./editors/expression/types";
import { useInputOutputContext } from "./io/InputOutputContext";
import { StyledLoadingButton } from "./node-action-buttons/StyledLoadingButton";
import { findParameters } from "./NodeDetailsContent/helpers";
import { getFindAvailableVariables } from "./NodeDetailsContent/selectors";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

interface Props {
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
}

export function OpenApiEnricherDataMapper({ node, parameterDefinitions, setProperty }: Props): React.JSX.Element | null {
    const [showDataMapper] = useUserSettings("node.showDataMapper");
    const [open, setOpen] = useState(false);

    const findAvailableVariables = useAppSelector(getFindAvailableVariables);
    const variableTypes = useMemo(() => findAvailableVariables(node.id), [findAvailableVariables, node.id]);

    const ioContext = useInputOutputContext();
    const initialContext = useMemo<ContextData | undefined>(() => {
        const [contexts] = ioContext?.getAvailableContexts("input") ?? [[]];
        if (!contexts.length) return undefined;
        const vars = contexts[0].variables;
        return Object.fromEntries(Object.keys(vars).map((k) => [k, vars[k].pretty]));
    }, [ioContext]);

    // Show only for enrichers with a dynamic service selector (OpenAPI pattern)
    const hasDynamicSelector = useMemo(() => parameterDefinitions.some((p) => p.changesCanReloadParameters), [parameterDefinitions]);

    // Parameters that are actual service inputs (not the selector itself)
    const mappableParams = useMemo(() => parameterDefinitions.filter((p) => !p.changesCanReloadParameters), [parameterDefinitions]);

    const initialFields = useMemo<FieldDef[] | undefined>(() => {
        if (mappableParams.length === 0) return undefined;
        const currentParams = findParameters(node);
        return mappableParams.map((p) => {
            const type = refClazzToNuType(p.typ?.refClazzName);
            const field = makeField(p.name, type);
            const current = currentParams.find((np) => np.name === p.name);
            field.expression = current?.expression?.expression?.trim() ?? "";
            return field;
        });
    }, [mappableParams, node]);

    const handleInsert = useCallback(
        (spel: string) => {
            const parsed = parseSpelToFields(spel);
            if (!parsed) return;
            const currentParams = findParameters(node);
            for (const field of parsed) {
                const paramIndex = currentParams.findIndex((p) => p.name === field.name);
                if (paramIndex >= 0) {
                    setProperty(`service.parameters[${paramIndex}].expression`, {
                        expression: field.expression || "null",
                        language: ExpressionLang.SpEL,
                    });
                }
            }
            setOpen(false);
        },
        [node, setProperty],
    );

    if (!showDataMapper || !hasDynamicSelector || mappableParams.length === 0) return null;

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
                            initialFields={initialFields}
                            fetchTopicDefinitions={() => Promise.resolve([])}
                            hideFieldControls
                        />
                    </DialogContent>
                </Dialog>
            )}
        </>
    );
}
