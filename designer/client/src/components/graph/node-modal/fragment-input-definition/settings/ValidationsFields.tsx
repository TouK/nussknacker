import React from "react";
import { t } from "i18next";
import { Switch } from "@mui/material";
import { Option } from "../FieldsSelect";
import { UpdatedItem, onChangeType } from "../item";
import ValidationFields from "./ValidationFields";
import { SettingRow, SettingLabelStyled } from "./StyledSettingsComponnets";

interface ValidationsFields {
    item: UpdatedItem;
    currentOption: Option;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
}

export default function ValidationsFields({ onChange, currentOption, path, item }: ValidationsFields) {
    return (
        <>
            {!currentOption?.value.includes("String") &&
                !currentOption?.value.includes("Boolean") &&
                item?.inputMode !== "Any value with suggestions" && (
                    <SettingRow>
                        <SettingLabelStyled>{t("fragment.validation.validation", "Validation:")}</SettingLabelStyled>
                        <Switch value={item.validation} onChange={() => onChange(`${path}.validation`, !item.validation)} />
                        <div style={{ width: "100%", justifyContent: "flex-end", display: "flex" }}>
                            <SettingLabelStyled style={{ flexBasis: "70%", minWidth: "70%" }}>
                                {t(
                                    "fragment.validation.validationWarning",
                                    "When validation is enabled, the parameter's value will be evaluated and validated at deployment time. In run-time, Nussknacker will use this precalculated value for each processed data record.",
                                )}
                            </SettingLabelStyled>
                        </div>
                    </SettingRow>
                )}
            {item.validation && (
                <ValidationFields
                    path={path}
                    onChange={onChange}
                    validatioErrorMessage={item.validatioErrorMessage}
                    validationExpression={item.validationExpression}
                />
            )}
        </>
    );
}
