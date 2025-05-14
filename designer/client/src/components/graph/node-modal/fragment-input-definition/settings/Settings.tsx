import React from "react";

import type { NodeValidationError, VariableTypes } from "../../../../../types";
import type { onChangeType, FragmentInputParameter } from "../item";
import { isPermittedTypeVariant, toFullRefClazzName } from "../item";
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
        const item = toFullRefClazzName(props.item);
        return <PermittedTypeVariant {...props} item={item} />;
    }
    return <DefaultVariant {...props} item={props.item} />;
}
