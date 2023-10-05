import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Parameter } from "../../../../types";
import { mandatoryValueValidator, uniqueListValueValidator, Validator } from "../editors/Validators";
import { DndItems } from "./DndItems";
import { NodeRowFields } from "./NodeRowFields";
import Item from "./item/Item";

export interface Option {
    value: string;
    label: string;
}

interface FieldsSelectProps {
    addField: () => void;
    fields: Parameter[];
    label: string;
    namespace: string;
    onChange: (path: string, value: any) => void;
    options: Option[];
    removeField: (path: string, index: number) => void;
    readOnly?: boolean;
    showValidation?: boolean;
}

export interface UpdatedFields extends Parameter {
    settingsOpen?: boolean;
    currentOption?: string;
    settingsOptionsForCurrentType?: any;
}

function FieldsSelect(props: FieldsSelectProps): JSX.Element {
    const { addField, fields, label, onChange, namespace, options, readOnly, removeField, showValidation } = props;
    const [updatedFields, setUpdatedFields] = useState(
        fields.map((field) => ({
            ...field,
            settingsOpen: false,
            currentOption: undefined,
        })),
    );

    useEffect(() => {
        setUpdatedFields(
            fields.map((field, index) => ({
                ...field,
                settingsOpen: updatedFields[index]?.settingsOpen ?? false,
                currentOption: updatedFields[index]?.currentOption ?? undefined,
            })),
        );
    }, [fields]);

    const updatedCurrentField = (
        currentIndex: number,
        settingsOpen: boolean,
        selectedSettingOption: string,
        settingsOptionsForCurrentType: any,
    ) => {
        const currentUpdateFields = updatedFields.map((field, index) => {
            if (index === currentIndex) {
                return {
                    ...field,
                    settingsOpen,
                    currentOption: selectedSettingOption,
                    settingsOptionsForCurrentType,
                };
            }
            return field;
        });
        setUpdatedFields(currentUpdateFields);
    };

    const ItemEl = useCallback(
        ({ index, item, validators }: { index: number; item: UpdatedFields; validators: Validator[] }) => {
            return (
                <Item
                    index={index}
                    item={item}
                    validators={validators}
                    namespace={namespace}
                    onChange={onChange}
                    options={options}
                    updatedCurrentField={updatedCurrentField}
                    readOnly={readOnly}
                    showValidation={showValidation}
                />
            );
        },
        [namespace, onChange, options, readOnly, showValidation],
    );

    const changeOrder = useCallback((value) => onChange(namespace, value), [namespace, onChange]);

    const items = useMemo(
        () =>
            updatedFields.map((item, index, list) => {
                const validators = [
                    mandatoryValueValidator,
                    uniqueListValueValidator(
                        list.map((v) => v.name),
                        index,
                    ),
                ];

                return { item, el: <ItemEl key={index} index={index} item={item} validators={validators} /> };
            }),
        [Item, updatedFields],
    );

    return (
        <NodeRowFields label={label} path={namespace} onFieldAdd={addField} onFieldRemove={removeField} readOnly={readOnly}>
            <DndItems disabled={readOnly} items={items} onChange={changeOrder} />
        </NodeRowFields>
    );
}

export default FieldsSelect;
