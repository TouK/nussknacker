import React from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { onChangeType, FragmentInputParameter, FixedValuesOption } from "../../../item";
import { Option, TypeSelect } from "../../../TypeSelect";
import { NodeInput } from "../../../../../../withFocus";

interface InitialValue {
    item: FragmentInputParameter;
    path: string;
    onChange: (path: string, value: onChangeType) => void;
    options?: FixedValuesOption[];
    readOnly: boolean;
}

export default function InitialValue({ onChange, item, path, options = [], readOnly }: InitialValue) {
    const { t } = useTranslation();

    const emptyOption = { label: "", value: null };
    const optionsToDisplay: Option[] = [emptyOption, ...options.map(({ label }) => ({ label, value: label }))];

    return (
        <SettingRow>
            <SettingLabelStyled>{t("fragment.initialValue", "Initial value:")}</SettingLabelStyled>
            {options.length > 0 ? (
                <TypeSelect
                    onChange={(value) => {
                        const selectedOption = options.find((option) => option.label === value);
                        onChange(`${path}.initialValue`, selectedOption);
                    }}
                    value={optionsToDisplay.find((option) => option.value === item?.initialValue?.label)}
                    options={optionsToDisplay}
                    readOnly={readOnly}
                    placeholder={""}
                />
            ) : (
                <NodeInput
                    style={{ width: "70%" }}
                    value={item?.initialValue?.label}
                    onChange={(event) =>
                        onChange(`${path}.initialValue`, { label: event.currentTarget.value, expression: event.currentTarget.value })
                    }
                    readOnly={readOnly}
                />
            )}
        </SettingRow>
    );
}
