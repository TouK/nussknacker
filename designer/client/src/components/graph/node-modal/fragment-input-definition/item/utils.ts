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
        // fixedValuesType: FixedValuesType.UserDefinedList,
        valueEditor: null,
        // fixedValuesListPresetId: "",
        // presetSelection: "",
        valueCompileTimeValidation: null,
        typ: { refClazzName } as ReturnedType,
    };
};
