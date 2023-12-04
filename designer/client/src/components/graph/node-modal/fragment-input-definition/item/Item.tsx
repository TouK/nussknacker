import React, { useCallback } from "react";
import { isEqual } from "lodash";
import { TypeSelect } from "../TypeSelect";
import { Validator, Error } from "../../editors/Validators";
import { Option } from "../FieldsSelect";
import { FixedValuesPresets, ReturnedType, VariableTypes } from "../../../../../types";
import SettingsButton from "../buttons/SettingsButton";
import { FieldsRow } from "../FieldsRow";
import { Settings } from "../settings/Settings";
import { useDiffMark } from "../../PathsToMark";
import { onChangeType, FragmentInputParameter, InputMode } from "./types";
import { useFieldsContext } from "../../node-row-fields-provider";
import Input from "../../editors/field/Input";
import { NodeValue } from "../../node";
import { SettingsProvider } from "../settings/SettingsProvider";

interface ItemProps {
    index: number;
    item: FragmentInputParameter;
    validators: Validator[];
    namespace: string;
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    options: Option[];
    fixedValuesPresets: FixedValuesPresets;
    fieldsErrors: Error[];
}

export function Item(props: ItemProps): JSX.Element {
    const {
        index,
        item,
        validators,
        namespace,
        variableTypes,
        readOnly,
        showValidation,
        onChange,
        options,
        fixedValuesPresets,
        fieldsErrors,
    } = props;
    const { getIsOpen, toggleIsOpen } = useFieldsContext();

    const isOpen = getIsOpen(item.uuid);

    const path = `${namespace}[${index}]`;
    const [isMarked] = useDiffMark();
    const getCurrentOption = useCallback(
        (typ: ReturnedType | undefined) => {
            const fallbackValue = { label: typ?.refClazzName, value: typ?.refClazzName };
            const foundValue = options.find((item) => isEqual(typ?.refClazzName, item.value));
            return foundValue || fallbackValue;
        },
        [options],
    );

    const openSettingMenu = () => {
        toggleIsOpen(item.uuid);
    };

    return (
        <div>
            <FieldsRow index={index} uuid={item.uuid}>
                <NodeValue>
                    <Input
                        readOnly={readOnly}
                        showValidation={showValidation}
                        isMarked={isMarked(`${path}.name`)}
                        onChange={(e) => onChange(`${path}.name`, e.target.value)}
                        value={item.name}
                        validators={validators}
                        placeholder="Field name"
                    />
                </NodeValue>
                <TypeSelect
                    readOnly={readOnly}
                    onChange={(value) => {
                        onChange(`${path}.typ.refClazzName`, value);
                        onChange(`${path}.valueEditor`, null);
                    }}
                    value={getCurrentOption(item.typ)}
                    isMarked={isMarked(`${path}.typ.refClazzName`)}
                    options={options}
                />
                <SettingsButton isOpen={isOpen} toggleIsOpen={openSettingMenu} />
            </FieldsRow>
            <SettingsProvider>
                {isOpen && (
                    <Settings
                        path={path}
                        item={item}
                        onChange={onChange}
                        variableTypes={variableTypes}
                        fixedValuesPresets={fixedValuesPresets}
                        readOnly={readOnly}
                        fieldsErrors={fieldsErrors}
                        data-testid={`settings:${index}`}
                    />
                )}
            </SettingsProvider>
        </div>
    );
}
