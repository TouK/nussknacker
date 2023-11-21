import React, { useCallback, useMemo } from "react";
import { FixedValuesPresets, Parameter, VariableTypes } from "../../../../types";
import { mandatoryValueValidator, uniqueListValueValidator, Validator } from "../editors/Validators";
import { DndItems } from "../../../common/dndItems/DndItems";
import { NodeRowFieldsProvider } from "../node-row-fields-provider";
import { Item, onChangeType, FragmentInputParameter, GroupedFieldsErrors } from "./item";
import { Error } from "../editors/Validators";
import { chain } from "lodash";

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
    removeField: (path: string, uuid: string) => void;
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
    fixedValuesPresets: FixedValuesPresets;
    fieldErrors: Error[];
}

export function FieldsSelect(props: FieldsSelectProps): JSX.Element {
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
        fieldErrors,
    } = props;

    const ItemElement = useCallback(
        ({
            index,
            item,
            validators,
            fieldsErrors,
        }: {
            index: number;
            item: FragmentInputParameter;
            validators: Validator[];
            fieldsErrors: GroupedFieldsErrors;
        }) => {
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
                    fieldsErrors={fieldsErrors}
                />
            );
        },
        [namespace, onChange, options, readOnly, variableTypes, showValidation, fixedValuesPresets],
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

                const fieldsErrors: Record<string, Error[]> = chain(fieldErrors)
                    .filter((fieldError) => fieldError.fieldName.includes(`$param.${item.name}`))
                    .groupBy("fieldName")
                    .mapKeys((_, key) => key.replace(`$param.${item.name}.$`, ""))
                    .mapValues((errors, key) => errors.map((error) => ({ ...error, fieldName: key })))
                    .value();

                return {
                    item,
                    el: <ItemElement key={index} index={index} item={item} validators={validators} fieldsErrors={fieldsErrors} />,
                };
            }),
        [ItemElement, fieldErrors, fields],
    );

    return (
        <NodeRowFieldsProvider label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
        </NodeRowFieldsProvider>
    );
}
