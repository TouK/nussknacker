import React from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType, FixedValuesPresetOption } from "../../../item";
import { Option, TypeSelect } from "../../../TypeSelect";
import { NodeInput } from "../../../../../../withFocus";

interface InitialValue {
    item: UpdatedItem;
    path: string;
    onChange: (path: string, value: onChangeType) => void;
    options?: Option[];
}

export default function InitialValue({ onChange, item, path, options }: InitialValue) {
    const { t } = useTranslation();

    console.log("item.initialValue", item.initialValue);
    return (
        <SettingRow>
            <SettingLabelStyled>{t("fragment.initialValue", "Initial value:")}</SettingLabelStyled>
            {options ? (
                <TypeSelect
                    onChange={(value) => onChange(`${path}.initialValue`, value)}
                    value={options.find((option) => option.value === item.initialValue) || ""}
                    options={options}
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
