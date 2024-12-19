import React from "react";
import { onChangeType, FragmentInputParameter, isPermittedTypeVariant, toPermittedTypeVariant } from "../item";
import { NodeValidationError, VariableTypes } from "../../../../../types";
import { DefaultVariant, PermittedTypeVariant } from "./variants";

interface Settings {
    item: FragmentInputParameter;
    path: string;
    variableTypes: VariableTypes;
    onChange: (path: string, value: onChangeType) => void;
    readOnly: boolean;
    errors: NodeValidationError[];
}

export function Settings(props: Settings) {
    if (isPermittedTypeVariant(props.item)) {
        const item = toPermittedTypeVariant(props.item);
        return <PermittedTypeVariant {...props} item={item} />;
    }
    return <DefaultVariant {...props} item={props.item} />;
}
