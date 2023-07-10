import { isEqual } from "lodash";
import React, { useCallback, useMemo } from "react";
import { Parameter } from "../../../../types";
import MapKey from "../editors/map/MapKey";
import { mandatoryValueValidator, uniqueListValueValidator, Validator } from "../editors/Validators";
import { DndItems } from "./DndItems";
import { FieldsRow } from "./FieldsRow";
import { NodeRowFields } from "./NodeRowFields";
import { TypeSelect } from "./TypeSelect";
import { useDiffMark } from "../PathsToMark";

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

function FieldsSelect(props: FieldsSelectProps): JSX.Element {
    const { addField, fields, label, onChange, namespace, options, readOnly, removeField, showValidation } = props;
    const [isMarked] = useDiffMark();

    const getCurrentOption = useCallback(
        (field) => {
            const fallbackValue = { label: field?.typ?.refClazzName, value: field?.typ?.refClazzName };
            const foundValue = options.find((item) => isEqual(field?.typ?.refClazzName, item.value));
            return foundValue || fallbackValue;
        },
        [options],
    );

    const Item = useCallback(
        ({ index, item, validators }: { index: number; item; validators: Validator[] }) => {
            const path = `${namespace}[${index}]`;
            return (
                <FieldsRow index={index}>
                    <MapKey
                        readOnly={readOnly}
                        showValidation={showValidation}
                        isMarked={isMarked(`${path}.name`)}
                        onChange={(value) => onChange(`${path}.name`, value)}
                        value={item.name}
                        validators={validators}
                    />
                    <TypeSelect
                        readOnly={readOnly}
                        onChange={(value) => onChange(`${path}.typ.refClazzName`, value)}
                        value={getCurrentOption(item)}
                        isMarked={isMarked(`${path}.typ.refClazzName`)}
                        options={options}
                    />
                </FieldsRow>
            );
        },
        [getCurrentOption, isMarked, namespace, onChange, options, readOnly, showValidation],
    );

    const changeOrder = useCallback((value) => onChange(namespace, value), [namespace, onChange]);

    const items = useMemo(
        () =>
            fields.map((item, index, list) => {
                const validators = [
                    mandatoryValueValidator,
                    uniqueListValueValidator(
                        list.map((v) => v.name),
                        index,
                    ),
                ];

                return { item, el: <Item key={index} index={index} item={item} validators={validators} /> };
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
