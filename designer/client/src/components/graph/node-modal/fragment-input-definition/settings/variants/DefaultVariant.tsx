import React from "react";
import { CustomSwitch, SettingLabelStyled, SettingRow, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import { FormControlLabel } from "@mui/material";
import { useTranslation } from "react-i18next";
import { DefaultParameterVariant, onChangeType } from "../../item";
import { VariableTypes } from "../../../../../../types";
import { TextAreaNodeWithFocus } from "../../../../../withFocus";
import ValidationsFields from "./fields/ValidationsFields";
import InitialValue from "./fields/InitialValue";

interface Props {
    item: DefaultParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
}

export const DefaultVariant = ({ item, onChange, path, variableTypes, readOnly }: Props) => {
    const { t } = useTranslation();

    return (
        <SettingsWrapper>
            <SettingRow>
                <SettingLabelStyled required>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={<CustomSwitch checked={item.required} onChange={() => onChange(`${path}.required`, !item.required)} />}
                    label=""
                />
            </SettingRow>
            {/*<ValidationsFields path={path} onChange={onChange} item={item} variableTypes={variableTypes} />*/}
            <InitialValue onChange={onChange} item={item} path={path} readOnly={readOnly} />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={"node-input"}
                />
            </SettingRow>
        </SettingsWrapper>
    );
};
