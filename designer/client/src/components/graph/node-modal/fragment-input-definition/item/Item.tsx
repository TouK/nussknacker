import React, { useCallback, useMemo, useState } from "react";
import { isEqual } from "lodash";
import MapKey from "../../editors/map/MapKey";
import { TypeSelect } from "../TypeSelect";
import { Validator } from "../../editors/Validators";
import { Option, UpdatedFields } from "../FieldsSelect";
import { Parameter } from "../../../../../types";
import SettingsButton from "../SettingsButton";
import { FieldsRow } from "../FieldsRow";
import SettingsWithoutStringBool from "../settings/SettingsWithoutStringBool";
import { useDiffMark } from "../../PathsToMark";

interface ItemProps {
    index: number;
    item: UpdatedFields;
    validators: Validator[];
    namespace: string;
    readOnly?: boolean;
    showValidation?: boolean;
    onChange: (path: string, value: string) => void;
    options: Option[];
    updatedCurrentField: (currentIndex: number, settingsOpen: boolean, settingsOptions: any) => void;
}

function Item(props: ItemProps): JSX.Element {
    const { index, item, validators, namespace, readOnly, showValidation, onChange, options, updatedCurrentField } = props;
    const path = `${namespace}[${index}]`;
    const [isOpen, setIsOpen] = useState(false);
    const [isMarked] = useDiffMark();

    const getCurrentOption = useCallback(
        (field: Parameter) => {
            const fallbackValue = { label: field?.typ?.refClazzName, value: field?.typ?.refClazzName };
            const foundValue = options.find((item) => isEqual(field?.typ?.refClazzName, item.value));
            return foundValue || fallbackValue;
        },
        [options],
    );

    const validateFieldsForCurrentOption = (currentOption: string) => {
        const defaultOption = {
            required: false,
            hintText: "",
            initialValue: "",
        };
        if (currentOption === "string" || currentOption === "bool") {
            return {
                ...defaultOption,
                presetSelection: "List 1" || "any value with suggestions",
                inputMode: "Fixed list",
                mode: "Preset" || "User Defined list",
            };
        } else if (currentOption !== "string" && currentOption !== "boolean") {
            return {
                ...defaultOption,
                validation: true,
                validationExpression: "",
                validationErrorMessage: "",
            };
        }
    };

    const openSettingMenu = () => {
        const { value: currentOption } = getCurrentOption(item);
        console.log(currentOption, "currentOption");
        const fieldsForCurrentOption = validateFieldsForCurrentOption(currentOption);
        updatedCurrentField(index, !isOpen, "");
        setIsOpen(!isOpen);
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
                    onChange={(value) => onChange(`${path}.typ.refClazzName`, value)}
                    value={getCurrentOption(item)}
                    isMarked={isMarked(`${path}.typ.refClazzName`)}
                    options={options}
                />
                <SettingsButton isOpen={isOpen} openSettingMenu={openSettingMenu} />
            </FieldsRow>
            {isOpen && <SettingsWithoutStringBool item={item} currentOption={getCurrentOption(item)} />}
        </div>
    );
}

export default Item;
