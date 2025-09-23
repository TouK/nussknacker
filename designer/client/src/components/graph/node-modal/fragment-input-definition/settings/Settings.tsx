import React from "react";

import type { NodeValidationError, VariableTypes } from "../../../../../types/validation";
import type { onChangeType, FragmentInputParameter } from "../item/types";
import { isPermittedTypeVariant, toFullRefClazzName } from "../item/types";
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
