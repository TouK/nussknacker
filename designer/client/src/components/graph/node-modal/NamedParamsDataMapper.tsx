import React, { useCallback, useMemo } from "react";

import type { UIParameter } from "../../../types/definition";
import type { NodeType } from "../../../types/node";
import { makeField, parseSpelToFields, refClazzToNuType } from "../../dataMapper/dataMapperUtils";
import type { NuType } from "../../dataMapper/dataMapperUtils";
import { DataMapperComponent } from "./DataMapperComponent";
import { EditorType, ExpressionLang } from "./editors/expression/types";
import { findParameters } from "./NodeDetailsContent/helpers";
import type { SetProperty } from "./useNodeTypeDetailsContentLogic";

function resolveParamNuType(typ: UIParameter["typ"]): NuType {
    if ("union" in typ && typ.union?.length) {
        const types = new Set(typ.union.map((u) => refClazzToNuType(u.refClazzName)));
        if (types.size === 1) return [...types][0] as NuType;
    }
    return refClazzToNuType(typ?.refClazzName);
}

interface Props {
    node: NodeType;
    parameterDefinitions: UIParameter[];
    setProperty: SetProperty;
    parametersBasePath?: string;
}

export function NamedParamsDataMapper({
    node,
    parameterDefinitions,
    setProperty,
    parametersBasePath = "service.parameters",
}: Props): React.JSX.Element | null {
    const mappableParams = useMemo(
        () =>
            parameterDefinitions.filter(
                (p) => !p.changesCanReloadParameters && p.editors?.some((e) => e.type === EditorType.SPEL_PARAMETER_EDITOR),
            ),
        [parameterDefinitions],
    );

    const initialFields = useMemo(() => {
        if (mappableParams.length === 0) return undefined;
        const currentParams = findParameters(node);
        return mappableParams.map((p) => {
            const field = makeField(p.name, resolveParamNuType(p.typ));
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
                if (paramIndex >= 0 && field.expression) {
                    setProperty(`${parametersBasePath}[${paramIndex}].expression`, {
                        expression: field.expression,
                        language: ExpressionLang.SpEL,
                    });
                }
            }
        },
        [node, parametersBasePath, setProperty],
    );

    if (mappableParams.length === 0) return null;

    return <DataMapperComponent node={node} onInsert={handleInsert} initialFields={initialFields} hideFieldControls />;
}
