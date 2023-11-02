import { Fields, InputMode, UpdatedItem, onChangeType, AllValueExcludeStringAndBoolean, StringAndBoolean } from ".";

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
    };

    if (isStringOrBooleanVariant(currentOption) && inputMode !== "AnyValue") {
        return {
            ...defaultOption,
            inputMode,
            allowOnlyValuesFromFixedValuesList: true,
            fixedValuesList: [],
            presetSelection: "",
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
