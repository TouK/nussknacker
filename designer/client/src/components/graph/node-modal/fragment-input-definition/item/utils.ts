import { FixedValuesType, FragmentInputParameter, InputMode, StringOrBooleanParameterVariant } from ".";
import { ReturnedType } from "../../../../../types";

export const getDefaultFields = (refClazzName: string): FragmentInputParameter => {
    return {
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
