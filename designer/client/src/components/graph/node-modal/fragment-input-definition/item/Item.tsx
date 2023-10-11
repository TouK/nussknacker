import React, { useCallback } from "react";
import { isEqual } from "lodash";
import MapKey from "../../editors/map/MapKey";
import { TypeSelect } from "../TypeSelect";
import { Validator } from "../../editors/Validators";
import { Option } from "../FieldsSelect";
import { Parameter, VariableTypes } from "../../../../../types";
import SettingsButton from "../SettingsButton";
import { FieldsRow } from "../FieldsRow";
import Settings from "../settings/Settings";
import { useDiffMark } from "../../PathsToMark";
import { UpdatedItem, onChangeType } from "./";
import { addNewFields, validateFieldsForCurrentOption } from "./utils";

interface ItemProps {
    index: number;
    item: UpdatedItem;
    validators: Validator[];
    namespace: string;
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    options: Option[];
}

export function Item(props: ItemProps): JSX.Element {
    const { index, item, validators, namespace, variableTypes, readOnly, showValidation, onChange, options } = props;
    const path = `${namespace}[${index}]`;
    const [isMarked] = useDiffMark();

    const getCurrentOption = useCallback(
        (field: Parameter) => {
            const fallbackValue = { label: field?.typ?.refClazzName, value: field?.typ?.refClazzName };
            const foundValue = options.find((item) => isEqual(field?.typ?.refClazzName, item.value));
            return foundValue || fallbackValue;
        },
        [options],
    );

    const openSettingMenu = () => {
        onChange(`${path}.settingsOpen`, !item.settingsOpen);
        const { value } = getCurrentOption(item);
        const fields = validateFieldsForCurrentOption(value, item.inputMode);
        addNewFields(fields, item, onChange, path);
    };

    return (
        <div>
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
                    onChange={(value) => {
                        onChange(`${path}.typ.refClazzName`, value);

                        const fields = validateFieldsForCurrentOption(value, item.inputMode);
                        addNewFields(fields, item, onChange, path);
                    }}
                    value={getCurrentOption(item)}
                    isMarked={isMarked(`${path}.typ.refClazzName`)}
                    options={options}
                />
                <SettingsButton isOpen={item.settingsOpen} openSettingMenu={openSettingMenu} />
            </FieldsRow>
            {item.settingsOpen && (
                <Settings
                    path={path}
                    item={item}
                    onChange={onChange}
                    currentOption={getCurrentOption(item)}
                    variableTypes={variableTypes}
                />
            )}
        </div>
    );
}
