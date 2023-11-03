import React from "react";
import { CustomSwitch, SettingLabelStyled, SettingRow, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import { FormControlLabel } from "@mui/material";
import { useTranslation } from "react-i18next";
import { DefaultItemVariant, onChangeType } from "../../item";
import { VariableTypes } from "../../../../../../types";
import { TextAreaNodeWithFocus } from "../../../../../withFocus";
import ValidationsFields from "./fields/ValidationsFields";
import InitialValue from "./fields/InitialValue";

interface Props {
    item: DefaultItemVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
}

export const DefaultVariant = ({ item, onChange, path, variableTypes }: Props) => {
    const { t } = useTranslation();

    return (
        <SettingsWrapper>
            <SettingRow>
                <SettingLabelStyled>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={<CustomSwitch checked={item.required} onChange={() => onChange(`${path}.required`, !item.required)} />}
                    label=""
                />
            </SettingRow>
            <ValidationsFields path={path} onChange={onChange} item={item} variableTypes={variableTypes} />
            <InitialValue onChange={onChange} item={item} path={path} />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                />
            </SettingRow>
        </SettingsWrapper>
    );
};
