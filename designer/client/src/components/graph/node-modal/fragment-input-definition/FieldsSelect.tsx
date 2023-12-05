import React, { useCallback, useMemo } from "react";
import { Parameter } from "../../../../types";
import MapKey from "../editors/map/MapKey";
import { DndItems } from "./DndItems";
import { FieldsRow } from "./FieldsRow";
import { NodeRowFields } from "./NodeRowFields";
import { TypeSelect } from "./TypeSelect";
import { useDiffMark } from "../PathsToMark";
import { mandatoryValueValidator, uniqueListValueValidator, Validator } from "../editors/Validators";

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
            fieldsErrors: Error[];
        }) => {
            return (
                <Item
                    index={index}
                    item={item}
                    fieldErrors={validators
                            .filter((validator) => !validator.isValid(item.name))
                            .map((validator) => ({
                                message: validator.message(),
                                typ: "",
                                description: validator.description(),
                                fieldName: item.name,
                                errorType: "SaveAllowed",
                            }))}
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

                /*
                 * Display settings errors only when the name is correct, for now, the name is used in the fieldName to recognize the list item,
                 * but it can be a situation where that name is not unique or is empty in a few parameters, in this case, there is a problem with a correct error display
                 */
                const displayableErrors = allValid(validators, item.name) ? fieldErrors : [];
                return {
                    item,
                    el: <ItemElement key={index} index={index} item={item} validators={validators} fieldsErrors={displayableErrors} />,
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
