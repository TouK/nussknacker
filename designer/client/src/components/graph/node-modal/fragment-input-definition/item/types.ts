import { Expression, ReturnedType } from "../../../../../types";

export type onChangeType = string | number | boolean | FixedValuesOption | FixedValuesOption[];

//TODO: Makes values required when backend ready
export interface FragmentValidation {
    validation?: boolean;
    validationErrorMessage?: string;
    validationExpression?: string;
}

export enum FixedValuesType {
    "ValueInputWithFixedValuesProvided" = "ValueInputWithFixedValuesProvided",
    "ValueInputWithFixedValuesPreset" = "ValueInputWithFixedValuesPreset",
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

interface ValueEditor {
    type: FixedValuesType;
    fixedValuesList: FixedValuesOption[] | null;
    allowOtherValue: boolean | null;
    fixedValuesPresetId: string | null;
}

export interface DefaultParameterVariant extends GenericParameterVariant, FragmentValidation {
    name: string;
    valueEditor: null;
}

export interface FixedListParameterVariant extends GenericParameterVariant {
    valueEditor: ValueEditor;
    fixedValuesListPresetId: string;
    presetSelection?: string;
}
export interface AnyValueWithSuggestionsParameterVariant extends GenericParameterVariant, FragmentValidation {
    valueEditor: ValueEditor;
    fixedValuesListPresetId: string;
    presetSelection?: string;
}
export interface AnyValueParameterVariant extends GenericParameterVariant, FragmentValidation {
    fixedValuesType: FixedValuesType;
    valueEditor: null;
}

export type StringOrBooleanParameterVariant =
    | FixedListParameterVariant
    | AnyValueWithSuggestionsParameterVariant
    | AnyValueParameterVariant;

export type FragmentInputParameter = StringOrBooleanParameterVariant | DefaultParameterVariant;

export function isFixedListParameter(item: StringOrBooleanParameterVariant): item is FixedListParameterVariant {
    return item.valueEditor?.allowOtherValue === false;
}

export function isAnyValueWithSuggestionsParameter(item: StringOrBooleanParameterVariant): item is AnyValueWithSuggestionsParameterVariant {
    return item?.valueEditor?.allowOtherValue === true;
}

export function isAnyValueParameter(item: StringOrBooleanParameterVariant): item is AnyValueParameterVariant {
    return item.valueEditor === null;
}

export function isStringOrBooleanVariant(item: FragmentInputParameter): item is StringOrBooleanParameterVariant {
    return item.typ.refClazzName.includes("String") || item.typ.refClazzName.includes("Boolean");
}

export type FieldName = `$param.${string}.$${string}`;
