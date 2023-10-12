import { Fields, InputMode, UpdatedItem, onChangeType } from ".";

export const addNewFields = (fields: Fields, item: UpdatedItem, onChange: (path: string, value: onChangeType) => void, path: string) => {
    Object.entries(fields).map(([key, value]) => {
        if (!item[key]) {
            onChange(`${path}.${key}`, value);
        }
    });
};

export const validateFieldsForCurrentOption = (currentOption: string, inputMode: InputMode) => {
    const defaultOption = {
        required: false,
        hintText: "",
        initialValue: "",
    };

    if (isValidOption(currentOption) && inputMode !== "Any value with suggestions") {
        return {
            ...defaultOption,
            inputMode: "Fixed list" as InputMode,
            allowOnlyValuesFromFixedValuesList: true,
            presetSelection: "",
        };
    } else {
        return {
            ...defaultOption,
            validation: true,
            validationExpression: "",
            validationErrorMessage: "",
        };
    }
};

export const isValidOption = (value: string) => {
    return value?.includes("String") || value?.includes("Boolean");
};
