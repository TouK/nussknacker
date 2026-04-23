import React, { createContext, useCallback, useMemo } from "react";

import type { TypedObjectTypingResult } from "../../../../../types/definition";
import type { Field } from "../../../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../../../types/validation";
import { DndItems } from "../../../../common/dndItems/DndItems";
import { FieldsRow } from "../../fragment-input-definition/FieldsRow";
import { NodeRowFieldsProvider } from "../../node-row-fields-provider/NodeRowFieldsProvider";
import { useDiffMark } from "../../PathsToMark";
import type { SetProperty } from "../../useNodeTypeDetailsContentLogic";
import MapRow from "./MapRow";

interface MapProps<F extends Field> {
    setProperty?: SetProperty;
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
    errors: NodeValidationError[];
    fields: F[];
    label: string;
    namespace: string;
    addField: (namespace: string, field?: F) => void;
    removeField: (namespace: string, uuid: string) => void;
    expressionType?: Partial<TypedObjectTypingResult>;
    isValidating: boolean;
}

export const MapItemsCtx = createContext<{
    readOnly?: boolean;
    showValidation?: boolean;
    setProperty?: SetProperty;
    isMarked: (path: string) => boolean;
    errors: NodeValidationError[];
    variableTypes: VariableTypes;
    isValidating: boolean;
}>(null);

export function Map<F extends Field>({
    fields,
    label,
    setProperty,
    addField,
    removeField,
    namespace,
    readOnly,
    expressionType,
    showValidation,
    variableTypes,
    errors,
    isValidating,
}: MapProps<F>): React.JSX.Element {
    const [isMarked] = useDiffMark();

    const appendTypeInfo = useCallback(
        (
            expressionObj: F,
        ): F & {
            typeInfo: string;
        } => {
            const fields = expressionType?.fields;
            const typeInfo = fields ? fields[expressionObj.name]?.display : expressionType?.display;
            return {
                ...expressionObj,
                typeInfo: typeInfo,
            };
        },
        [expressionType?.display, expressionType?.fields],
    );

    const changeOrder = useCallback((value) => setProperty(namespace, value), [namespace, setProperty]);
    const changeValue = useCallback((path, value) => setProperty(`${namespace}${path}`, value), [namespace, setProperty]);

    const items = useMemo(
        () =>
            fields?.map(appendTypeInfo)?.map((item, index) => ({
                item,
                el: (
                    <FieldsRow uuid={item.uuid} index={index}>
                        <MapRow index={index} item={item} />
                    </FieldsRow>
                ),
            })),
        [appendTypeInfo, fields],
    );

    return (
        <NodeRowFieldsProvider label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <MapItemsCtx.Provider
                value={{
                    readOnly,
                    isMarked: (path) => isMarked(`${namespace}.${path}`),
                    setProperty: changeValue,
                    showValidation,
                    errors,
                    variableTypes,
                    isValidating,
                }}
            >
                <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
            </MapItemsCtx.Provider>
        </NodeRowFieldsProvider>
    );
}

export default Map;
