import React from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType } from "../item";
import { Option } from "../FieldsSelect";
import { TypeSelect } from "../TypeSelect";
import { isValidOption } from "../item/utils";
import { NodeInput } from "../../../../../components/withFocus";

interface InitialValue {
    item: UpdatedItem;
    path: string;
    currentOption: Option;
    onChange: (path: string, value: onChangeType) => void;
}

export default function InitialValue({ onChange, item, path, currentOption }: InitialValue) {
    const { t } = useTranslation();

    return (
        <SettingRow>
            <SettingLabelStyled>{t("fragment.initialValue", "Initial value:")}</SettingLabelStyled>
            {item.allowOnlyValuesFromFixedValuesList && isValidOption(currentOption.value) ? (
                <TypeSelect
                    onChange={(value) => onChange(`${path}.initialValue`, value)}
                    value={{ value: item.initialValue, label: item.initialValue }}
                    options={[{ value: "option", label: "option" }]}
                />
            ) : (
                <NodeInput
                    style={{ width: "70%" }}
                    value={item.initialValue}
                    onChange={(event) => onChange(`${path}.initialValue`, event.currentTarget.value)}
                />
            )}
        </SettingRow>
    );
}
