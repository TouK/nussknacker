import React from "react";
import { SettingLabelStyled, SettingRow } from "./StyledSettingsComponnets";
import { NodeInput, SelectNodeWithFocus } from "../../../../withFocus";
import { useTranslation } from "react-i18next";
import { UpdatedItem, onChangeType } from "../item";

interface InitialValue {
    item: UpdatedItem;
    path: string;
    onChange: (path: string, value: onChangeType) => void;
}

export default function InitialValue({ onChange, item, path }: InitialValue) {
    const { t } = useTranslation();

    return (
        <SettingRow>
            <SettingLabelStyled style={{ flexBasis: "30%" }}>{t("fragment.text4", "Initial value:")}</SettingLabelStyled>
            {item.presetType === "Preset" && item.inputMode === "Fixed list" ? (
                <SelectNodeWithFocus
                    value={item.initialValue}
                    onChange={(e) => onChange(`${path}.initialValue`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                >
                    <option key={""} value={""}>
                        {"option"}
                    </option>
                </SelectNodeWithFocus>
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
