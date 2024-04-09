import React from "react";
import { useGetAllDicts } from "../StringBooleanVariants/useGetAllDicts";
import { NodeValidationError, ReturnedType } from "../../../../../../../types";
import { FormControl } from "@mui/material";
import { SettingLabelStyled } from "./StyledSettingsComponnets";
import { TypeSelect } from "../../../TypeSelect";
import { getValidationErrorsForField } from "../../../../editors/Validators";
import { useTranslation } from "react-i18next";
import { onChangeType } from "../../../item";

interface Props {
    typ: ReturnedType;
    readOnly: boolean;
    dictId: string;
    path: string;
    onChange: (path: string, value: onChangeType) => void;
    errors: NodeValidationError[];
    name: string;
}
export const DictSelect = ({ typ, readOnly, dictId, onChange, path, errors, name }: Props) => {
    const { processDefinitionDicts } = useGetAllDicts({ typ });
    const { t } = useTranslation();

    return (
        <FormControl>
            <SettingLabelStyled required>{t("fragment.presetSelection", "Preset selection:")}</SettingLabelStyled>
            <TypeSelect
                readOnly={readOnly}
                onChange={(value) => {
                    onChange(`${path}.valueEditor.dictId`, value);
                    onChange(`${path}.initialValue`, null);
                }}
                value={processDefinitionDicts.find((presetListOption) => presetListOption.value === dictId) ?? null}
                options={processDefinitionDicts}
                fieldErrors={getValidationErrorsForField(errors, `$param.${name}.$dictId`)}
            />
        </FormControl>
    );
};
