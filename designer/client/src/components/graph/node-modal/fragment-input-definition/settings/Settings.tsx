import React from "react";
import { Option } from "../FieldsSelect";
import { InputMode, UpdatedItem, onChangeType } from "../item";
import { VariableTypes } from "../../../../../types";
import { isStringOrBooleanVariant } from "../item/utils";
import { DefaultVariant, StringBooleanVariant } from "./variants";

interface Settings {
    item: UpdatedItem;
    path: string;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    currentOption: Option;
}

export default function Settings(props: Settings) {
    const { currentOption } = props;

    if (isStringOrBooleanVariant(currentOption.value)) {
        return <StringBooleanVariant {...props} />;
    }

    return <DefaultVariant {...props} />;
}
