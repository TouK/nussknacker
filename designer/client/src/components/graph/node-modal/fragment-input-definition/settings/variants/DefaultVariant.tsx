import { FormControlLabel } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import type { NodeValidationError, VariableTypes } from "../../../../../../types/validation";
import { TextAreaNode } from "../../../../../FormElements";
import { FormControl } from "../../../editors/FormControl";
import { getValidationErrorsForField } from "../../../editors/Validators";
import { nodeInput } from "../../../NodeDetailsContent/NodeTableStyled";
import type { DefaultParameterVariant, onChangeType } from "../../item/types";
import InitialValue from "./fields/InitialValue";
import { CustomSwitch, SettingLabelStyled, SettingsWrapper } from "./fields/StyledSettingsComponnets";
import { ValidationsFields } from "./fields/validation";

interface Props {
    item: DefaultParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
    isValidating: boolean;
}

export const DefaultVariant = ({ item, onChange, path, variableTypes, readOnly, errors, isValidating, ...props }: Props) => {
    const { t } = useTranslation();

    return (
        <SettingsWrapper {...props}>
            <FormControl>
                <SettingLabelStyled required>{t("fragment.required", "Required:")}</SettingLabelStyled>
                <FormControlLabel
                    control={<CustomSwitch checked={item.required} onChange={() => onChange(`${path}.required`, !item.required)} />}
                    label=""
                />
            </FormControl>
            <InitialValue
                onChange={onChange}
                item={item}
                path={path}
                readOnly={readOnly}
                variableTypes={variableTypes}
                fieldErrors={getValidationErrorsForField(errors, `$param.${item.name}.$initialValue`)}
            />
            <FormControl>
                <SettingLabelStyled>{t("fragment.hintText", "Hint text:")}</SettingLabelStyled>
                <TextAreaNode
                    value={item.hintText}
                    onChange={(e) => onChange(`${path}.hintText`, e.currentTarget.value)}
                    style={{ width: "70%" }}
                    disabled={readOnly}
                    className={nodeInput}
                />
            </FormControl>
            <ValidationsFields
                path={path}
                onChange={onChange}
                item={item}
                variableTypes={variableTypes}
                readOnly={readOnly}
                errors={errors}
                isValidating={isValidating}
            />
        </SettingsWrapper>
    );
};
