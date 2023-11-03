import React, { useCallback, useState } from "react";
import { isEqual } from "lodash";
import MapKey from "../../editors/map/MapKey";
import { TypeSelect } from "../TypeSelect";
import { Validator } from "../../editors/Validators";
import { Option } from "../FieldsSelect";
import { ReturnedType, VariableTypes } from "../../../../../types";
import SettingsButton from "../buttons/SettingsButton";
import { FieldsRow } from "../FieldsRow";
import Settings from "../settings/Settings";
import { useDiffMark } from "../../PathsToMark";
import { onChangeType, PropertyItem } from "./";
import { addNewFields, validateFieldsForCurrentOption } from "./utils";

interface ItemProps {
    index: number;
    item: PropertyItem;
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
        (typ: ReturnedType | undefined) => {
            const fallbackValue = { label: typ?.refClazzName, value: typ?.refClazzName };
            const foundValue = options.find((item) => isEqual(typ?.refClazzName, item.value));
            return foundValue || fallbackValue;
        },
        [options],
    );

    const openSettingMenu = () => {
        setIsOpen((prevState) => !prevState);
        const fields = validateFieldsForCurrentOption(item);
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
                        const fields = validateFieldsForCurrentOption(item);
                        addNewFields(fields, item, onChange, path);
                    }}
                    value={getCurrentOption(item.typ)}
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
                    currentOption={getCurrentOption(item.typ)}
                    variableTypes={variableTypes}
                />
            )}
        </div>
    );
}
