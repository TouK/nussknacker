import React from "react";
import ValidationsFields from "../fields/ValidationsFields";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled, SettingRow } from "../fields/StyledSettingsComponnets";
import { TextAreaNodeWithFocus } from "../../../../../../withFocus";
import { onChangeType, UpdatedItem } from "../../../item";
import { VariableTypes } from "../../../../../../../types";
import { useTranslation } from "react-i18next";

interface Props {
    item: UpdatedItem;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
}

export const AnyValueVariant = ({ item, path, variableTypes, onChange }: Props) => {
    const { t } = useTranslation();

    return (
        <>
            <ValidationsFields path={path} item={item} onChange={onChange} variableTypes={variableTypes} />
            <InitialValue path={path} item={item} onChange={onChange} />
            <SettingRow>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNodeWithFocus
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                />
            </SettingRow>
        </>
    );
};
