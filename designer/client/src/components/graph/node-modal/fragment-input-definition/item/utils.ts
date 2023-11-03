import {
    AnyValueItemVariant,
    AnyValueWithSuggestionsItemVariant,
    DefaultItemVariant,
    FixedListItemVariant,
    FixedValuesType,
    GenericParameterVariant,
    InputMode,
    onChangeType,
    PropertyItem,
    StringOrBooleanItemVariant,
} from ".";

export const addNewFields = (
    fields: PropertyItem,
    item: PropertyItem,
    onChange: (path: string, value: onChangeType) => void,
    path: string,
) => {
    Object.entries(fields).map(([key, value]) => {
        if (!item[key]) {
            onChange(`${path}.${key}`, value);
        }
    });
};

export const validateFieldsForCurrentOption = (item: PropertyItem): PropertyItem => {
    const defaultOption: GenericParameterVariant = {
        name: "",
        required: false,
        hintText: "",
        initialValue: "",
        fixedValuesType: FixedValuesType.None,
    };

    if (isStringOrBooleanVariant(item)) {
        const inputMode = item.inputMode;
        switch (inputMode) {
            case InputMode.AnyValue: {
                return {
                    ...defaultOption,
                    inputMode: InputMode.AnyValue,
                    presetSelection: "",
                    validationExpression: "",
                    validationErrorMessage: "",
                    validation: true,
                } as AnyValueItemVariant;
            }
            case InputMode.AnyValueWithSuggestions: {
                return {
                    ...defaultOption,
                    inputMode: InputMode.AnyValueWithSuggestions,
                    fixedValuesList: [],
                    fixedValuesPresets: {},
                    presetSelection: "",
                    fixedValuesType: FixedValuesType.Preset,
                    fixedValuesListPresetId: "",
                    validationExpression: "",
                    validationErrorMessage: "",
                    validation: true,
                } as AnyValueWithSuggestionsItemVariant;
            }
            case InputMode.FixedList: {
                return {
                    ...defaultOption,
                    inputMode: InputMode.FixedList,
                    allowOnlyValuesFromFixedValuesList: true,
                    fixedValuesList: [],
                    fixedValuesPresets: {},
                    presetSelection: "",
                    fixedValuesType: FixedValuesType.Preset,
                    fixedValuesListPresetId: "",
                } as FixedListItemVariant;
            }
            default: {
                const exhaustiveCheck: never = inputMode;
                throw new Error(`Input mode ${exhaustiveCheck} is not handled`);
            }
        }
    }

    return {
        ...defaultOption,
        validationExpression: "",
        validationErrorMessage: "",
        validation: true,
    };
};

export function isStringOrBooleanVariant(item: PropertyItem): item is StringOrBooleanItemVariant {
    return Boolean((item as StringOrBooleanItemVariant).inputMode);
}
