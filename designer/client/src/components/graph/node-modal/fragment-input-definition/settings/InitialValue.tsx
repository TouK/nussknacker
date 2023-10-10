import React from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { NodeInput } from "../../../../withFocus";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType } from "../item";
import { Option } from "../FieldsSelect";
import { TypeSelect } from "../TypeSelect";
import { isValidOption } from "../item/utils";

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
            {item.presetType === "Preset" && item.inputMode === "Fixed list" && isValidOption(currentOption) ? (
                <TypeSelect
                    onChange={(value) => onChange(`${path}.initialValue`, value)}
                    value={{ value: item.initialValue, label: item.initialValue }}
                    options={[{ value: "option", label: "option" }]}
                />
            ) : (
                <NodeInput
                    value={item.initialValue}
                    onChange={(e) => onChange(`${path}.initialValue`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                />
            )}
        </SettingRow>
    );
}
