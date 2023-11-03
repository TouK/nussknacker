import { AllValueExcludeStringAndBoolean, Fields, FixedValuesType, InputMode, onChangeType, StringAndBoolean, UpdatedItem } from ".";

export const addNewFields = (fields: Fields, item: UpdatedItem, onChange: (path: string, value: onChangeType) => void, path: string) => {
    Object.entries(fields).map(([key, value]) => {
        if (!item[key]) {
            onChange(`${path}.${key}`, value);
        }
    });
};

export const validateFieldsForCurrentOption = (
    currentOption: string,
    inputMode: InputMode,
): AllValueExcludeStringAndBoolean | StringAndBoolean => {
    const defaultOption = {
        required: false,
        hintText: "",
        initialValue: "",
        fixedValuesType: "None",
    };

    if (isStringOrBooleanVariant(currentOption) && inputMode !== "AnyValue") {
        return {
            ...defaultOption,
            inputMode,
            allowOnlyValuesFromFixedValuesList: true,
            fixedValuesList: [],
            fixedValuesPresets: {},
            presetSelection: "",
            fixedValuesType: FixedValuesType.Preset,
        };
    }

    return {
        ...defaultOption,
        validationExpression: "",
        validationErrorMessage: "",
    };
};

export const isStringOrBooleanVariant = (value: string) => {
    return value?.includes("String") || value?.includes("Boolean");
};
