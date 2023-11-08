import React from "react";
import { onChangeType, FragmentInputParameter } from "../item";
import { FixedValuesPresets, VariableTypes } from "../../../../../types";
import { isStringOrBooleanVariant } from "../item/utils";
import { DefaultVariant, StringBooleanVariant } from "./variants";

interface Settings {
    item: FragmentInputParameter;
    path: string;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    fixedValuesPresets: FixedValuesPresets;
}

export default function Settings(props: Settings) {
    if (isStringOrBooleanVariant(props.item)) {
        return <StringBooleanVariant {...props} item={props.item} />;
    }

    return <DefaultVariant {...props} item={props.item} />;
}
