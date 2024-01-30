import React, { useCallback } from "react";
import { isEqual } from "lodash";
import { TypeSelect } from "../TypeSelect";
import { getValidationErrorsForField } from "../../editors/Validators";
import { Option } from "../FieldsSelect";
import { FixedValuesPresets, NodeValidationError, ReturnedType, VariableTypes } from "../../../../../types";
import SettingsButton from "../buttons/SettingsButton";
import { FieldsRow } from "../FieldsRow";
import { Settings } from "../settings/Settings";
import { useDiffMark } from "../../PathsToMark";
import { onChangeType, FragmentInputParameter } from "./types";
import { useFieldsContext } from "../../node-row-fields-provider";
import Input from "../../editors/field/Input";
import { NodeValue } from "../../node";
import { SettingsProvider } from "../settings/SettingsProvider";

interface ItemProps {
    index: number;
    item: FragmentInputParameter;
    namespace: string;
    readOnly?: boolean;
    showValidation?: boolean;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    options: Option[];
    fixedValuesPresets: FixedValuesPresets;
    errors: NodeValidationError[];
}

export function Item(props: ItemProps): JSX.Element {
    const { index, item, namespace, variableTypes, readOnly, showValidation, onChange, options, fixedValuesPresets, errors } = props;
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
                        placeholder="Field name"
                        fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$name`)}
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
                    fieldErrors={getValidationErrorsForField(errors, `type`)}
                />
                <SettingsButton isOpen={isOpen} toggleIsOpen={openSettingMenu} />
            </FieldsRow>
            <SettingsProvider
                initialFixedValuesList={item?.valueEditor?.fixedValuesList}
                initialTemporaryValueCompileTimeValidation={item?.valueCompileTimeValidation}
            >
                {isOpen && (
                    <Settings
                        path={path}
                        item={item}
                        onChange={onChange}
                        variableTypes={variableTypes}
                        fixedValuesPresets={fixedValuesPresets}
                        readOnly={readOnly}
                        errors={errors}
                        data-testid={`settings:${index}`}
                    />
                )}
            </SettingsProvider>
        </div>
    );
}
