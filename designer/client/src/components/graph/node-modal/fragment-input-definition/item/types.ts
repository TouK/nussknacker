import { Parameter } from "../../../../../types";

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
export type InputMode = "Fixed list" | "Any value with suggestions" | "Any value";

export interface StringAndBoolean extends DefaultItemType, FragmentValidation {
    allowOnlyValuesFromFixedValuesList: boolean;
    fixedValueList?: string[];
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
