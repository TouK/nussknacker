import React, { createContext, useCallback, useMemo } from "react";
import { Field, TypedObjectTypingResult, VariableTypes } from "../../../../../types";
import { NodeRowFields } from "../../fragment-input-definition/NodeRowFields";
import { Error, mandatoryValueValidator, uniqueListValueValidator } from "../Validators";
import { useDiffMark } from "../../PathsToMark";
import { DndItems } from "../../../../common/dndItems/DndItems";
import MapRow from "./MapRow";
import { FieldsRow } from "../../fragment-input-definition/FieldsRow";

interface MapProps<F extends Field> {
    setProperty: (path: string, newValue: unknown) => void;
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
    fieldErrors: Error[];
    fields: F[];
    label: string;
    namespace: string;
    addField: (namespace: string, field?: F) => void;
    removeField: (namespace: string, index: number) => void;
    expressionType?: Partial<TypedObjectTypingResult>;
}

export const MapItemsCtx = createContext<{
    readOnly?: boolean;
    showValidation?: boolean;
    setProperty: (path: string, newValue: unknown) => void;
    isMarked: (path: string) => boolean;
    fieldErrors: Error[];
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
    fieldErrors,
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

    const uniqueNameValidator = useCallback(
        (index: number) =>
            uniqueListValueValidator(
                fields?.map((v) => v.name),
                index,
            ),
        [fields],
    );

    const items = useMemo(
        () =>
            fields?.map(appendTypeInfo)?.map((item, index) => ({
                item,
                el: (
                    <FieldsRow index={index}>
                        <MapRow index={index} item={item} />
                    </FieldsRow>
                ),
            })),
        [appendTypeInfo, fields],
    );

    return (
        <NodeRowFields label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <MapItemsCtx.Provider
                value={{
                    readOnly,
                    isMarked: (path) => isMarked(`${namespace}.${path}`),
                    setProperty: (path, value) => setProperty(`${namespace}.${path}`, value),
                    showValidation,
                    fieldErrors,
                    variableTypes,
                }}
            >
                <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
            </MapItemsCtx.Provider>
        </NodeRowFields>
    );
}

export default Map;
