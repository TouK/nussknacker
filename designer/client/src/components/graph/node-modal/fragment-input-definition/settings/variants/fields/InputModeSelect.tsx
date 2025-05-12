import { FormControl } from "@mui/material";
import { isNil } from "lodash";
import React from "react";
import { useTranslation } from "react-i18next";

import type { NodeValidationError } from "../../../../../../../types";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import type { Option } from "../../../FieldsSelect";
import type { onChangeType, PermittedTypeParameterVariant, ValueEditor } from "../../../item";
import { FixedValuesType, InputMode } from "../../../item";
import { TypeSelect } from "../../../TypeSelect";
import { useSettings } from "../../SettingsProvider";
import { SettingLabelStyled } from "./StyledSettingsComponnets";

interface Props {
    onChange: (path: string, value: onChangeType) => void;
    item: PermittedTypeParameterVariant;
    path: string;
    inputModeOptions: Option[];
    readOnly: boolean;
    errors: NodeValidationError[];
}

export default function InputModeSelect(props: Props) {
    const { onChange, path, item, inputModeOptions, errors } = props;
    const { t } = useTranslation();
    const { temporaryUserDefinedList } = useSettings();

    const value = isNil(item.valueEditor)
        ? InputMode.AnyValue
        : item.valueEditor.allowOtherValue
        ? InputMode.AnyValueWithSuggestions
        : InputMode.FixedList;
    return (
        <>
            <FormControl>
                <SettingLabelStyled required>{t("fragment.settings.inputMode", "Input mode:")}</SettingLabelStyled>
                <TypeSelect
                    readOnly={props.readOnly}
                    onChange={(value: InputMode) => {
                        const fixedValuesList =
                            item.valueEditor?.fixedValuesList?.length > 0 ? item.valueEditor.fixedValuesList : temporaryUserDefinedList;

                        const setFixedValuesTypeInitialValue = () => {
                            if (item?.valueEditor?.type === FixedValuesType.ValueInputWithFixedValuesProvided) {
                                return onChange(
                                    `${path}.initialValue`,
                                    fixedValuesList.find((fixedValuesList) => fixedValuesList.label === item?.initialValue?.label)
                                        ? item.initialValue
                                        : null,
                                );
                            }

                            if (item?.valueEditor?.type === FixedValuesType.ValueInputWithDictEditor) {
                                return onChange(`${path}.initialValue`, item.initialValue);
                            }
                        };

                        const setValueEditor = ({ allowOtherValue }: { allowOtherValue: boolean }): ValueEditor => {
                            return {
                                allowOtherValue,
                                fixedValuesList,
                                dictId: item?.valueEditor?.dictId || "",
                                type: item?.valueEditor?.type || FixedValuesType.ValueInputWithDictEditor,
                            };
                        };

                        switch (value) {
                            case InputMode.AnyValue: {
                                onChange(`${path}.valueEditor`, null);
                                onChange(`${path}.initialValue`, null);
                                break;
                            }
                            case InputMode.AnyValueWithSuggestions: {
                                onChange(`${path}.valueEditor`, setValueEditor({ allowOtherValue: true }));
                                setFixedValuesTypeInitialValue();
                                break;
                            }

                            case InputMode.FixedList: {
                                onChange(`${path}.valueEditor`, setValueEditor({ allowOtherValue: false }));
                                setFixedValuesTypeInitialValue();
                            }
                        }
                    }}
                    value={inputModeOptions.find((inputModeOption) => inputModeOption.value === value)}
                    options={inputModeOptions}
                    fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$inputMode`)}
                />
            </FormControl>
        </>
    );
}
