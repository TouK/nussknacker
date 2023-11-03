import React, { useCallback, useState } from "react";
import { isEqual } from "lodash";
import MapKey from "../../editors/map/MapKey";
import { TypeSelect } from "../TypeSelect";
import { Validator } from "../../editors/Validators";
import { Option } from "../FieldsSelect";
import { Parameter, VariableTypes } from "../../../../../types";
import SettingsButton from "../buttons/SettingsButton";
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
    const [isOpen, setIsOpen] = useState(false);
    const getCurrentOption = useCallback(
        (field: Parameter) => {
            const fallbackValue = { label: field?.typ?.refClazzName, value: field?.typ?.refClazzName };
            const foundValue = options.find((item) => isEqual(field?.typ?.refClazzName, item.value));
            return foundValue || fallbackValue;
        },
        [options],
    );

    const openSettingMenu = () => {
        const { value } = getCurrentOption(item);
        setIsOpen((prevState) => !prevState);
        const fields = validateFieldsForCurrentOption(value, item.inputMode);
        addNewFields(fields, item, onChange, path);
    };

    return (
        <div>
            <FieldsRow index={index}>
                <MapKey
                    className="parametersFieldName"
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
                        const fields = validateFieldsForCurrentOption(value, "selectedInputMode");
                        addNewFields(fields, item, onChange, path);
                    }}
                    value={getCurrentOption(item)}
                    isMarked={isMarked(`${path}.typ.refClazzName`)}
                    options={options}
                />
                <SettingsButton isOpen={isOpen} openSettingMenu={openSettingMenu} />
            </FieldsRow>
            {isOpen && (
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
