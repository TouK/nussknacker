import { FragmentInputParameter } from ".";
import { ReturnedType } from "../../../../../types";
import { v4 as uuid4 } from "uuid";

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
