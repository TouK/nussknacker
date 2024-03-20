import React, { createContext, useCallback, useMemo } from "react";
import { Field, NodeType, NodeValidationError, TypedObjectTypingResult, VariableTypes } from "../../../../../types";
import { NodeRowFieldsProvider } from "../../node-row-fields-provider";
import { useDiffMark } from "../../PathsToMark";
import { DndItems } from "../../../../common/dndItems/DndItems";
import MapRow from "./MapRow";
import { FieldsRow } from "../../fragment-input-definition/FieldsRow";

interface MapProps<F extends Field> {
    setProperty?: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
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
}

export const MapItemsCtx = createContext<{
    readOnly?: boolean;
    showValidation?: boolean;
    setProperty?: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void;
    isMarked: (path: string) => boolean;
    errors: NodeValidationError[];
    variableTypes: VariableTypes;
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
}: MapProps<F>): JSX.Element {
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
                    setProperty: (path, value) => setProperty(`${namespace}.${path}`, value),
                    showValidation,
                    errors,
                    variableTypes,
                }}
            >
                <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
            </MapItemsCtx.Provider>
        </NodeRowFieldsProvider>
    );
}

export default Map;
