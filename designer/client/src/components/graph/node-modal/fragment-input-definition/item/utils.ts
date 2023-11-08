import { FixedValuesType, FragmentInputParameter, StringOrBooleanParameterVariant } from ".";

export const getDefaultFields = (refClazzName: string): FragmentInputParameter => {
    return {
        allowOnlyValuesFromFixedValuesList: false,
        isOpen: false,
        inputMode: undefined,
        name: "",
        required: false,
        hintText: "",
        initialValue: "",
        fixedValuesType: FixedValuesType.Preset,
        validationExpression: "",
        fixedValuesList: [],
        fixedValuesListPresetId: "",
        presetSelection: "",
        validationErrorMessage: "",
        validation: true,
        typ: { display: "", type: "", params: [], refClazzName },
    };
};

export function isStringOrBooleanVariant(item: FragmentInputParameter): item is StringOrBooleanParameterVariant {
    return item.typ.refClazzName.includes("String") || item.typ.refClazzName.includes("Boolean");
}
