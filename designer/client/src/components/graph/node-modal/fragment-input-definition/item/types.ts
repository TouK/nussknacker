import { Expression, ReturnedType } from "../../../../../types";

export type onChangeType = string | number | boolean | FixedValuesOption | FixedValuesOption[];

//TODO: Makes values required when backend ready
export interface FragmentValidation {
    validation?: boolean;
    validationErrorMessage?: string;
    validationExpression?: string;
}

export enum FixedValuesType {
    "Preset" = "Preset",
    "UserDefinedList" = "UserDefinedList",
    "None" = "None",
}

export enum InputMode {
    "FixedList" = "InputModeFixedList",
    "AnyValueWithSuggestions" = "InputModeAnyWithSuggestions",
    "AnyValue" = "InputModeAny",
}

export interface FixedValuesOption {
    expression: string;
    label: string;
}

export interface GenericParameterVariant {
    uuid: string;
    required: boolean;
    name: string;
    typ?: ReturnedType;
    initialValue: FixedValuesOption | null;
    hintText: string | null;
    //It's only to satisfy typescript
    expression?: Expression;
}

export interface DefaultParameterVariant extends GenericParameterVariant, FragmentValidation {
    name: string;
}

export interface FixedListParameterVariant extends GenericParameterVariant {
    inputConfig: {
        inputMode: InputMode.FixedList;
        fixedValuesList: FixedValuesOption[];
    };
    fixedValuesListPresetId: string;
    presetSelection?: string;
    fixedValuesType: FixedValuesType;
}
export interface AnyValueWithSuggestionsParameterVariant extends GenericParameterVariant, FragmentValidation {
    inputConfig: {
        inputMode: InputMode.AnyValueWithSuggestions;
        fixedValuesList: FixedValuesOption[];
    };
    fixedValuesListPresetId: string;
    presetSelection?: string;
    fixedValuesType: FixedValuesType;
}
export interface AnyValueParameterVariant extends GenericParameterVariant, FragmentValidation {
    inputConfig: {
        inputMode: InputMode.AnyValue;
        fixedValuesList: FixedValuesOption[];
    };
    fixedValuesType: FixedValuesType;
}

export type StringOrBooleanParameterVariant =
    | FixedListParameterVariant
    | AnyValueWithSuggestionsParameterVariant
    | AnyValueParameterVariant;

export type FragmentInputParameter = StringOrBooleanParameterVariant | DefaultParameterVariant;

export function isFixedListParameter(item: StringOrBooleanParameterVariant): item is FixedListParameterVariant {
    return item.inputConfig.inputMode === InputMode.FixedList;
}

export function isAnyValueWithSuggestionsParameter(item: StringOrBooleanParameterVariant): item is AnyValueWithSuggestionsParameterVariant {
    return item.inputConfig.inputMode === InputMode.AnyValueWithSuggestions;
}

export function isAnyValueParameter(item: StringOrBooleanParameterVariant): item is AnyValueParameterVariant {
    return item.inputConfig.inputMode === InputMode.AnyValue;
}
