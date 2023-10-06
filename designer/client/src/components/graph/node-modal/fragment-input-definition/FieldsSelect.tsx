import React, { useCallback, useMemo, useState } from "react";
import { NodeType, Parameter } from "../../../../types";
import { mandatoryValueValidator, uniqueListValueValidator, Validator } from "../editors/Validators";
import { DndItems } from "./DndItems";
import { NodeRowFields } from "./NodeRowFields";
import Item from "./item/Item";
import { find, head, set, cloneDeep } from "lodash";
import { useSelector } from "react-redux";
import ProcessUtils from "../../../../common/ProcessUtils";
import { getProcessDefinitionData } from "../../../../reducers/selectors/settings";
import { ArrayElement } from "../NodeTypeDetailsContent";

export interface Option {
    value: string;
    label: string;
}

interface FieldsSelectProps {
    addField: () => void;
    node: NodeType<Parameter>;
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
    settingsOptions?: any;
}

const useFieldsAction = (node: NodeType<Parameter>) => {
    const definitionData = useSelector(getProcessDefinitionData);
    const [localNode, setLocalNode] = useState(node);
    const fields = useMemo(
        () =>
            localNode.parameters.map((field: UpdatedFields) => ({
                ...field,
                settingsOpen: field?.settingsOpen ?? false,
                settingsOptions: field?.settingsOptions ?? undefined,
            })) || [],
        [localNode.parameters],
    );

    const removeField = useCallback(
        (property: keyof NodeType, index: number): void => {
            setLocalNode((currentFields) => ({
                ...currentFields,
                [property]: currentFields[property]?.filter((_, i) => i !== index) || [],
            }));
        },
        [setLocalNode],
    );

    const addElement = useCallback(
        <K extends keyof NodeType>(property: K, element: ArrayElement<NodeType[K]>): void => {
            setLocalNode((currentFields) => ({
                ...currentFields,
                [property]: [...currentFields[property], element],
            }));
        },
        [setLocalNode],
    );

    const typeOptions = useMemo(
        () =>
            definitionData?.processDefinition?.typesInformation?.map((type) => ({
                value: type.clazzName.refClazzName,
                label: ProcessUtils.humanReadableType(type.clazzName),
            })),
        [definitionData?.processDefinition?.typesInformation],
    );
    const defaultTypeOption = useMemo(() => find(typeOptions, { label: "String" }) || head(typeOptions), [typeOptions]);

    const addField = useCallback(() => {
        addElement("parameters", { name: "", typ: { refClazzName: defaultTypeOption.value } } as Parameter);
    }, [addElement, defaultTypeOption.value]);

    const updatedCurrentField = (currentIndex: number, settingsOpen: boolean, settingsOptions: any) => {
        const currentUpdateFields = fields.map((field, index) => {
            if (index === currentIndex) {
                return {
                    ...field,
                    settingsOpen,
                    settingsOptions,
                };
            }
            return field;
        });
        // setUpdatedFields(currentUpdateFields);
    };

    const onChange = useCallback(
        <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]): void => {
            setLocalNode((currentNode) => {
                const value = newValue == null && defaultValue != undefined ? defaultValue : newValue;
                const node = cloneDeep(currentNode);
                return set(node, property, value);
            });
        },
        [setLocalNode],
    );

    return { fields, addField, removeField, updatedCurrentField, onChange };
};

function FieldsSelect(props: FieldsSelectProps): JSX.Element {
    const { node, label, namespace, options, readOnly, showValidation } = props;
    const { addField, removeField, fields, updatedCurrentField, onChange } = useFieldsAction(node);

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
            fields.map((item, index, list) => {
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
