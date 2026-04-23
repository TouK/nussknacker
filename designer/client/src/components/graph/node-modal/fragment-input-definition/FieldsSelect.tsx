import React, { useCallback, useMemo } from "react";

import type { Parameter } from "../../../../types/node";
import type { NodeValidationError, VariableTypes } from "../../../../types/validation";
import { DndItems } from "../../../common/dndItems/DndItems";
import { NodeRowFieldsProvider } from "../node-row-fields-provider/NodeRowFieldsProvider";
import { Item } from "./item/Item";
import type { FragmentInputParameter, onChangeType } from "./item/types";

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
    errors: NodeValidationError[];
    isValidating: boolean;
}

export function FieldsSelect(props: FieldsSelectProps): React.JSX.Element {
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
        errors,
        isValidating,
    } = props;

    const ItemElement = useCallback(
        ({ index, item, errors }: { index: number; item: FragmentInputParameter; errors: NodeValidationError[] }) => (
            <Item
                index={index}
                item={item}
                namespace={namespace}
                onChange={onChange}
                options={options}
                readOnly={readOnly}
                variableTypes={variableTypes}
                showValidation={showValidation}
                errors={errors}
                isValidating={isValidating}
            />
        ),
        [namespace, onChange, options, readOnly, variableTypes, showValidation, isValidating],
    );

    const changeOrder = useCallback((value) => onChange(namespace, value), [namespace, onChange]);

    const items = useMemo(
        () =>
            fields.map((item, index) => ({
                item,
                el: (
                    <ItemElement
                        key={item.uuid || item.name || index}
                        index={index}
                        item={item as FragmentInputParameter}
                        errors={errors}
                    />
                ),
            })),
        [ItemElement, errors, fields],
    );

    return (
        <NodeRowFieldsProvider label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
        </NodeRowFieldsProvider>
    );
}
