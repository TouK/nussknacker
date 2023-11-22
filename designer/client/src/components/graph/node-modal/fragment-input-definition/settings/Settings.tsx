import React from "react";
import { onChangeType, FragmentInputParameter, isStringOrBooleanVariant, FragmentFieldsErrors } from "../item";
import { FixedValuesPresets, VariableTypes } from "../../../../../types";
import { DefaultVariant, StringBooleanVariant } from "./variants";

interface Settings {
    item: FragmentInputParameter;
    path: string;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    fixedValuesPresets: FixedValuesPresets;
    readOnly: boolean;
    fieldsErrors: FragmentFieldsErrors;
}

export function Settings(props: Settings) {
    if (isStringOrBooleanVariant(props.item)) {
        return <StringBooleanVariant {...props} item={props.item} />;
    }

    return <DefaultVariant {...props} item={props.item} />;
}
