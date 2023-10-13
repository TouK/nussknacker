import React, { useEffect, useMemo, useState } from "react";
import { t } from "i18next";
import { Option } from "../FieldsSelect";
import { FormControlLabel } from "@mui/material";
import ValidationFields from "./ValidationFields";
import { InputMode, UpdatedItem, onChangeType } from "../item";
import { SettingRow, SettingLabelStyled, CustomSwitch } from "./StyledSettingsComponnets";
import { VariableTypes } from "../../../../../types";

interface ValidationsFields {
    item: UpdatedItem;
    currentOption: Option;
    onChange: (path: string, value: onChangeType) => void;
    path: string;
    variableTypes: VariableTypes;
    selectedInputMode: InputMode;
}

export default function ValidationsFields(props: ValidationsFields) {
    const { onChange, currentOption, path, variableTypes, item, selectedInputMode } = props;
    const [validation, setValidation] = useState(true);

    const showValidation = useMemo(
        () =>
            (!currentOption?.value.includes("String") && !currentOption?.value.includes("Boolean")) ||
            selectedInputMode === "Any value with suggestions",
        [currentOption, selectedInputMode],
    );

    return (
        <>
            {showValidation && (
                <SettingRow>
                    <SettingLabelStyled>{t("fragment.validation.validation", "Validation:")}</SettingLabelStyled>
                    <FormControlLabel
                        control={<CustomSwitch checked={validation} onChange={(event) => setValidation(event.currentTarget.checked)} />}
                        label=""
                    />
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
            {showValidation && validation && (
                <ValidationFields
                    path={path}
                    onChange={onChange}
                    validationErrorMessage={item.validationErrorMessage}
                    validationExpression={item.validationExpression}
                    variableTypes={variableTypes}
                />
            )}
        </>
    );
}
