import { Parameter } from "../../../../../types";
import { Option } from "../TypeSelect";

export type UpdatedItem = StringAndBoolean & AllValueExcludeStringAndBoolean;

export type Fields = DefaultFields | DefaultFieldsWithValidation;

export type onChangeType = string | number | boolean | string[];

export interface DefaultItemType extends Parameter {
    required: boolean;
    initialValue: string;
    hintText: string;
    settingsOpen: boolean;
}

export interface FragmentValidation {
    validation: boolean;
    validationErrorMessage: string;
    validationExpression: string;
}

export interface AllValueExcludeStringAndBoolean extends FragmentValidation, DefaultItemType {}

export type PresetType = "Preset" | "User defined list";
export enum InputMode {
    "FixedList" = "FixedList",
    "AnyValueWithSuggestions" = "AnyValueWithSuggestions",
    "AnyValue" = "AnyValue",
}

export interface StringAndBoolean extends DefaultItemType, FragmentValidation {
    allowOnlyValuesFromFixedValuesList: boolean;
    fixedValueList?: Option[];
    presetSelection: string;
}

interface DefaultFields {
    allowOnlyValuesFromFixedValuesList: boolean;
    fixedValueList?: string[];
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
