import { Fields, InputMode, PresetType, UpdatedItem, onChangeType } from ".";

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

    if (currentOption.includes("String") || currentOption.includes("Boolean")) {
        return {
            ...defaultOption,
            inputMode: "Fixed list" as InputMode,
            presetType: "Preset" as PresetType,
            presetSelection: "",
        };
    } else if (!currentOption.includes("String") && !currentOption.includes("Boolean") && inputMode !== "Any value with suggestions") {
        return {
            ...defaultOption,
            validation: true,
            validationExpression: "",
            validationErrorMessage: "",
        };
    }
};
