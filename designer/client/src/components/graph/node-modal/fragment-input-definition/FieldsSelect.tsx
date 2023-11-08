import React, { useCallback, useMemo } from "react";
import { FixedValuesPresets, Parameter, VariableTypes } from "../../../../types";
import { mandatoryValueValidator, uniqueListValueValidator, Validator } from "../editors/Validators";
import { DndItems } from "../../../common/dndItems/DndItems";
import { NodeRowFields } from "./NodeRowFields";
import { Item, onChangeType, FragmentInputParameter } from "./item";

export interface Option {
    value: string;
    label: string;
}

interface FieldsSelectProps {
    addField: () => void;
    label: string;
    fields: Parameter[];
    namespace: string;
    onChange: (path: string, value: onChangeType) => void;
    options: Option[];
    removeField: (path: string, index: number) => void;
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
    fixedValuesPresets: FixedValuesPresets;
}

function FieldsSelect(props: FieldsSelectProps): JSX.Element {
    const {
        fields,
        label,
        namespace,
        options,
        onChange,
        variableTypes,
        removeField,
        addField,
        readOnly,
        showValidation,
        fixedValuesPresets,
    } = props;

    const ItemElement = useCallback(
        ({ index, item, validators }: { index: number; item: FragmentInputParameter; validators: Validator[] }) => {
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
                    fixedValuesPresets={fixedValuesPresets}
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

                return { item, el: <ItemElement key={index} index={index} item={item} validators={validators} /> };
            }),
        [ItemElement, fields],
    );

    return (
        <NodeRowFields label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
        </NodeRowFields>
    );
}

export default FieldsSelect;
