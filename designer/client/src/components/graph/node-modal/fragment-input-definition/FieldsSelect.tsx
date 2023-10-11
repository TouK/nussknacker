import React, { SetStateAction, useCallback, useMemo } from "react";
import { NodeType, Parameter, VariableTypes } from "../../../../types";
import { mandatoryValueValidator, uniqueListValueValidator, Validator } from "../editors/Validators";
import { DndItems } from "./DndItems";
import { NodeRowFields } from "./NodeRowFields";
import { Item, UpdatedItem } from "./item";

export interface Option {
    value: string;
    label: string;
}

interface FieldsSelectProps {
    addField: () => void;
    removeField: (path: string, index: number) => void;
    label: string;
    fields: Parameter[];
    namespace: string;
    onChange: (propToMutate: string, newValue: string) => void;
    setEditedNode: (n: SetStateAction<NodeType>) => void;
    options: Option[];
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
}

function FieldsSelect(props: FieldsSelectProps): JSX.Element {
    const { fields, label, namespace, options, onChange, variableTypes, removeField, addField, readOnly, showValidation } = props;

    const ItemEl = useCallback(
        ({ index, item, validators }: { index: number; item: UpdatedItem; validators: Validator[] }) => {
            return (
                <Item
                    index={index}
                    item={item}
                    validators={validators}
                    namespace={namespace}
                    onChange={onChange}
                    options={options}
                    readOnly={readOnly}
                    variableTypes={variableTypes}
                    showValidation={showValidation}
                />
            );
        },
        [namespace, onChange, options, variableTypes, readOnly, showValidation],
    );

    const changeOrder = useCallback((value) => onChange(namespace, value), [namespace, onChange]);

    const items = useMemo(
        () =>
            fields.map((item: any, index, list) => {
                const validators = [
                    mandatoryValueValidator,
                    uniqueListValueValidator(
                        list.map((v) => v.name),
                        index,
                    ),
                ];

                return { item, el: <ItemEl key={index} index={index} item={item} validators={validators} /> };
            }),
        [Item, fields],
    );

    return (
        <NodeRowFields label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
        </NodeRowFields>
    );
}

export default FieldsSelect;
