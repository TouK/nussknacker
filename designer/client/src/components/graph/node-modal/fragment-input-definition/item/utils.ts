import { v4 as uuid4 } from "uuid";

import type { FragmentInputParameter } from ".";
import type { ReturnedType } from "../../../../../types";

//This projection is used for backward-compatibility reasons, since previously fragment input definition type options display part contained full class name
export function resolveRefClazzName(refClazzName: string): string {
    const parts = refClazzName.split(".");
    return parts[parts.length - 1];
}

export const getDefaultFields = (refClazzName: string): FragmentInputParameter => {
    return {
        uuid: uuid4(),
        name: "",
        required: false,
        hintText: "",
        initialValue: undefined,
        valueEditor: undefined,
        valueCompileTimeValidation: undefined,
        typ: { refClazzName } as ReturnedType,
    };
};
