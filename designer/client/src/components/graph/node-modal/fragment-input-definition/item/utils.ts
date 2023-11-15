import { FragmentInputParameter, StringOrBooleanParameterVariant } from ".";
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
        inputConfig: {
            fixedValuesList: [],
            inputMode: null,
        },
        // fixedValuesListPresetId: "",
        // validationExpression: "test",
        // presetSelection: "",
        // validationExpression: "",
        // validationErrorMessage: "",
        // validation: true,
        typ: { refClazzName } as ReturnedType,
    };
};

export function isStringOrBooleanVariant(item: FragmentInputParameter): item is StringOrBooleanParameterVariant {
    return item.typ.refClazzName.includes("String") || item.typ.refClazzName.includes("Boolean");
}
