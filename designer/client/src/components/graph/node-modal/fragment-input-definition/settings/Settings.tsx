import React from "react";
import { onChangeType, PropertyItem } from "../item";
import { VariableTypes } from "../../../../../types";
import { isStringOrBooleanVariant } from "../item/utils";
import { DefaultVariant, StringBooleanVariant } from "./variants";

interface Settings {
    item: PropertyItem;
    path: string;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
}

export default function Settings(props: Settings) {
    if (isStringOrBooleanVariant(props.item)) {
        return <StringBooleanVariant {...props} item={props.item} />;
    }

    return <DefaultVariant {...props} item={props.item} />;
}
