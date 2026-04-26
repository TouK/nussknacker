import React from "react";
import { useTranslation } from "react-i18next";

import type { NodeValidationError, VariableTypes } from "../../../../../../../types/validation";
import { TextAreaNode } from "../../../../../../FormElements";
import { FormControl } from "../../../../editors/FormControl";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { nodeInput } from "../../../../NodeDetailsContent/NodeTableStyled";
import type { AnyValueParameterVariant, onChangeType } from "../../../item/types";
import InitialValue from "../fields/InitialValue";
import { SettingLabelStyled } from "../fields/StyledSettingsComponnets";
import { ValidationsFields } from "../fields/validation";

interface Props {
    item: AnyValueParameterVariant;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export const AnyValueVariant = ({ item, path, onChange, readOnly, variableTypes, errors }: Props) => {
    const { t } = useTranslation();

    return (
        <>
            <InitialValue
                path={path}
                item={item}
                onChange={onChange}
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
                item={item}
                onChange={onChange}
                variableTypes={variableTypes}
                readOnly={readOnly}
                errors={errors}
            />
        </>
    );
};
