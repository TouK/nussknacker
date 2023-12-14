import React, { useCallback, useMemo } from "react";
import { FixedValuesPresets, NodeValidationError, Parameter, VariableTypes } from "../../../../types";
import { mandatoryValueValidator, uniqueListValueValidator, extendErrors } from "../editors/Validators";
import { DndItems } from "../../../common/dndItems/DndItems";
import { NodeRowFieldsProvider } from "../node-row-fields-provider";
import { Item, onChangeType, FragmentInputParameter } from "./item";
import { isEmpty } from "lodash";

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
    errors: NodeValidationError[];
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
        errors,
    } = props;

    const ItemElement = useCallback(
        ({ index, item, errors }: { index: number; item: FragmentInputParameter; errors: NodeValidationError[] }) => {
            return (
                <Item
                    index={index}
                    item={item}
                    namespace={namespace}
                    onChange={onChange}
                    options={options}
                    readOnly={readOnly}
                    variableTypes={variableTypes}
                    showValidation={showValidation}
                    fixedValuesPresets={fixedValuesPresets}
                    errors={errors}
                />
            );
        },
        [namespace, onChange, options, readOnly, variableTypes, showValidation, fixedValuesPresets],
    );

    const changeOrder = useCallback((value) => onChange(namespace, value), [namespace, onChange]);

    const items = useMemo(
        () =>
            fields.map((item: any, index, list) => {
                const nameErrors = extendErrors([], item.name, "name", [
                    mandatoryValueValidator,
                    uniqueListValueValidator(
                        list.map((v) => v.name),
                        index,
                    ),
                ]);

                /*
                 * Display settings errors only when the name is correct, for now, the name is used in the fieldName to recognize the list item,
                 * but it can be a situation where that name is not unique or is empty in a few parameters, in this case, there is a problem with a correct error display
                 */
                const displayableErrors = isEmpty(nameErrors) ? errors : nameErrors;
                return {
                    item,
                    el: <ItemElement key={index} index={index} item={item} errors={displayableErrors} />,
                };
            }),
        [ItemElement, errors, fields],
    );

    return (
        <NodeRowFieldsProvider label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
        </NodeRowFieldsProvider>
    );
}
