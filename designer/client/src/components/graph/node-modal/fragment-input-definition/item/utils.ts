import { FragmentInputParameter } from ".";
import { ReturnedType } from "../../../../../types";
import { v4 as uuid4 } from "uuid";

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
        initialValue: null,
        valueEditor: null,
        valueCompileTimeValidation: null,
        typ: { refClazzName } as ReturnedType,
    };
};
