import { Expression, ReturnedType } from "../../../../../types";

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
    //It's only to satisfy typescript
    expression?: Expression;
}

export interface DefaultParameterVariant extends GenericParameterVariant, FragmentValidation {
    name: string;
}

export interface FixedListParameterVariant extends GenericParameterVariant {
    inputMode?: InputMode.FixedList;
    fixedValuesList?: FixedValuesOption[];
    fixedValuesListPresetId: string;
    presetSelection: string;
    fixedValuesType: FixedValuesType;
}
export interface AnyValueWithSuggestionsParameterVariant extends GenericParameterVariant, FragmentValidation {
    inputMode?: InputMode.AnyValueWithSuggestions;
    fixedValuesList?: FixedValuesOption[];
    fixedValuesListPresetId: string;
    presetSelection: string;
    fixedValuesType: FixedValuesType;
}
export interface AnyValueParameterVariant extends GenericParameterVariant, FragmentValidation {
    inputMode?: InputMode.AnyValue;
    fixedValuesType: FixedValuesType;
}

export type StringOrBooleanParameterVariant =
    | FixedListParameterVariant
    | AnyValueWithSuggestionsParameterVariant
    | AnyValueParameterVariant;

export type FragmentInputParameter = StringOrBooleanParameterVariant | DefaultParameterVariant;
