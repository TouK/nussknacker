import React from "react";
import { SettingLabelStyled, SettingRow, SettingsWrapper } from "./StyledSettingsComponnets";
import { Switch } from "@mui/material";
import { NodeInput } from "../../../../../components/withFocus";
import { useTranslation } from "react-i18next";
import { UpdatedFields } from "../FieldsSelect";

interface SettingsWithoutStringBool {
    item: UpdatedFields;
}

export default function SettingsWithoutStringBool({ item }: SettingsWithoutStringBool) {
    const { t } = useTranslation();

    return (
        <SettingsWrapper>
            <SettingRow>
                <SettingLabelStyled style={{ flexBasis: "30%" }}>{t("fragment.text1", "Required:")}</SettingLabelStyled>
                <Switch />
            </SettingRow>
            {(item.currentOption?.includes("String") || item.currentOption?.includes("Boolean")) && (
                <div>
                    <p>Hello</p>
                </div>
            )}
            <SettingRow>
                <SettingLabelStyled style={{ flexBasis: "30%" }}>{t("fragment.text2", "Validation:")}</SettingLabelStyled>
                <Switch />
                <div style={{ width: "100%", justifyContent: "flex-end", display: "flex" }}>
                    <SettingLabelStyled style={{ flexBasis: "70%", minWidth: "70%" }}>
                        {t(
                            "fragment.text6",
                            "When validation is enabled, the parameter's value will be evaluated and validated at deployment time. In run-time, Nussknacker will use this precalculated value for each processed data record.",
                        )}
                    </SettingLabelStyled>
                </div>
            </SettingRow>
            <SettingRow>
                <SettingLabelStyled style={{ flexBasis: "30%" }}>{t("fragment.text2", "Validation expression:")}</SettingLabelStyled>
                <NodeInput style={{ width: "70%" }} />
            </SettingRow>
            <SettingRow>
                <SettingLabelStyled style={{ flexBasis: "30%" }}>{t("fragment.text3", "Validation error message:")}</SettingLabelStyled>
                <NodeInput style={{ width: "70%" }} />
            </SettingRow>
            <SettingRow>
                <SettingLabelStyled style={{ flexBasis: "30%" }}>{t("fragment.text4", "Initial value:")}</SettingLabelStyled>
                <NodeInput style={{ width: "70%" }} />
            </SettingRow>
            <SettingRow>
                <SettingLabelStyled style={{ flexBasis: "30%" }}>{t("fragment.text5", "Hint text:")}</SettingLabelStyled>
                <NodeInput style={{ width: "70%" }} />
            </SettingRow>
        </SettingsWrapper>
    );
}
