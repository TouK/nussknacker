import { ReturnedType } from "../../../../../types";

export type onChangeType = string | number | boolean | FixedValuesOption[];

export interface FragmentValidation {
    validation: boolean;
    validationErrorMessage: string;
    validationExpression: string;
}

export enum FixedValuesType {
    "Preset" = "Preset",
    "UserDefinedList" = "UserDefinedList",
    "None" = "None",
}

export enum InputMode {
    "FixedList" = "FixedList",
    "AnyValueWithSuggestions" = "AnyValueWithSuggestions",
    "AnyValue" = "AnyValue",
}

export type FixedValuesPresetOption = Record<string, FixedValuesOption[]>;

export interface FixedValuesOption {
    expression: string;
    label: string;
}

export interface GenericParameterVariant {
    required: boolean;
    name: string;
    typ?: ReturnedType;
    initialValue: string | undefined;
    hintText: string | undefined;
    isOpen?: boolean;
}

export interface DefaultItemVariant extends GenericParameterVariant, FragmentValidation {
    name: string;
}

export interface FixedListItemVariant extends GenericParameterVariant {
    inputMode: InputMode.FixedList;
    fixedValuesList: FixedValuesOption[];
    fixedValuesPresets: FixedValuesPresetOption;
    allowOnlyValuesFromFixedValuesList: boolean;
    fixedValuesListPresetId: string;
    presetSelection: string;
    fixedValuesType: FixedValuesType;
}
export interface AnyValueWithSuggestionsItemVariant extends GenericParameterVariant, FragmentValidation {
    inputMode: InputMode.AnyValueWithSuggestions;
    fixedValuesList: FixedValuesOption[];
    fixedValuesPresets: FixedValuesPresetOption | undefined;
    fixedValuesListPresetId: string;
    presetSelection: string;
    fixedValuesType: FixedValuesType;
}
export interface AnyValueItemVariant extends GenericParameterVariant, FragmentValidation {
    inputMode: InputMode.AnyValue;
    fixedValuesType: FixedValuesType;
}

export type StringOrBooleanItemVariant = FixedListItemVariant | AnyValueWithSuggestionsItemVariant | AnyValueItemVariant;

export type PropertyItem = StringOrBooleanItemVariant | DefaultItemVariant;
