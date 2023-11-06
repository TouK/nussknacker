import { FixedValuesType, PropertyItem, StringOrBooleanItemVariant } from ".";

export const getDefaultFields = (refClazzName: string): PropertyItem => {
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
        fixedValuesPresets: {},
        presetSelection: "",
        validationErrorMessage: "",
        validation: true,
        typ: { display: "", type: "", params: [], refClazzName },
    };
};

export function isStringOrBooleanVariant(item: PropertyItem): item is StringOrBooleanItemVariant {
    return item.typ.refClazzName.includes("String") || item.typ.refClazzName.includes("Boolean");
}
