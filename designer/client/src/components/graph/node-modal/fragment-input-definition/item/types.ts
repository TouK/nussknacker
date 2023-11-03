import { Parameter } from "../../../../../types";

export type UpdatedItem = StringAndBoolean & AllValueExcludeStringAndBoolean;

export type Fields = DefaultFields | DefaultFieldsWithValidation;

export type onChangeType = string | number | boolean | FixedValuesOption[];

export interface DefaultItemType extends Parameter {
    required: boolean;
    initialValue: string;
    hintText: string;
}

export interface FragmentValidation {
    validation: boolean;
    validationErrorMessage: string;
    validationExpression: string;
}

export interface AllValueExcludeStringAndBoolean extends FragmentValidation, DefaultItemType {}

export enum PresetType {
    "Preset" = "Preset",
    "UserDefinedList" = "UserDefinedList",
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

export interface StringAndBoolean extends DefaultItemType, FragmentValidation {
    allowOnlyValuesFromFixedValuesList: boolean;
    fixedValuesList?: FixedValuesOption[];
    fixedValuesPresets?: FixedValuesPresetOption;
    inputMode: InputMode;
    presetSelection: string;
    fixedValuesListPresetId: string;
}

interface DefaultFields {
    allowOnlyValuesFromFixedValuesList: boolean;
    fixedValuesList?: string[];
    presetSelection: string;
    required: boolean;
    hintText: string;
    initialValue: string;
}

interface DefaultFieldsWithValidation {
    validationExpression: string;
    validationErrorMessage: string;
}

// NEW ONE
interface ParameterDetails {
    required: boolean;
    validation: boolean;
    validationExpression: string;
    validationErrorMessage: string;
    initialValue: string | undefined;
    hintText: string | undefined;
}

interface FixedListParameterDetails {
    inputMode: InputMode.FixedList;
    required: boolean;
    presetType: PresetType;
}
interface AnyValueWithSuggestionsParameterDetails {
    inputMode: InputMode.AnyValueWithSuggestions;
    presetType: PresetType;
}
interface AnyValueParameterDetails {
    inputMode: InputMode.AnyValue;
    presetType: PresetType;
}
