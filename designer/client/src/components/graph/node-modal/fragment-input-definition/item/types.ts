import { Expression, ReturnedType } from "../../../../../types";
import { resolveRefClazzName } from "./utils";

export type onChangeType = string | number | boolean | FixedValuesOption | FixedValuesOption[] | ValueCompileTimeValidation | ValueEditor;

export interface ValueCompileTimeValidation {
    validationExpression: Expression;
    validationFailedMessage?: string;
}

export interface FragmentValidation {
    valueCompileTimeValidation: ValueCompileTimeValidation | null;
}

export enum FixedValuesType {
    "ValueInputWithFixedValuesProvided" = "ValueInputWithFixedValuesProvided",
    "ValueInputWithDictEditor" = "ValueInputWithDictEditor",
}

export enum InputMode {
    "FixedList" = "InputModeFixedList",
    "AnyValueWithSuggestions" = "InputModeAnyWithSuggestions",
    "AnyValue" = "InputModeAny",
}

export interface FixedValuesOption {
    expression: string;
    label: string;
    icon?: string;
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

export interface ValueEditor {
    type: FixedValuesType;
    fixedValuesList: FixedValuesOption[] | null;
    allowOtherValue: boolean | null;
    dictId: string;
}

export interface DefaultParameterVariant extends GenericParameterVariant, FragmentValidation {
    name: string;
    valueEditor: null;
}

export interface FixedListParameterVariant extends GenericParameterVariant, FragmentValidation {
    valueEditor: ValueEditor;
    presetSelection?: string;
}
export interface AnyValueWithSuggestionsParameterVariant extends GenericParameterVariant, FragmentValidation {
    valueEditor: ValueEditor;
    presetSelection?: string;
}
export interface AnyValueParameterVariant extends GenericParameterVariant, FragmentValidation {
    fixedValuesType: FixedValuesType;
    valueEditor: null;
}

export type PermittedTypeParameterVariant = FixedListParameterVariant | AnyValueWithSuggestionsParameterVariant | AnyValueParameterVariant;

export type FragmentInputParameter = PermittedTypeParameterVariant | DefaultParameterVariant;

export function isFixedListParameter(item: PermittedTypeParameterVariant): item is FixedListParameterVariant {
    return item.valueEditor?.allowOtherValue === false;
}

export function isAnyValueWithSuggestionsParameter(item: PermittedTypeParameterVariant): item is AnyValueWithSuggestionsParameterVariant {
    return item?.valueEditor?.allowOtherValue === true;
}

export function isAnyValueParameter(item: PermittedTypeParameterVariant): item is AnyValueParameterVariant {
    return item.valueEditor === null;
}

export function isPermittedTypeVariant(item: FragmentInputParameter): item is PermittedTypeParameterVariant {
    return [
        resolveRefClazzName(item.typ.refClazzName) === "String",
        resolveRefClazzName(item.typ.refClazzName) === "Boolean",
        resolveRefClazzName(item.typ.refClazzName) === "Long",
        resolveRefClazzName(item.typ.refClazzName) === "Integer",
    ].includes(true);
}

export type FieldName = `$param.${string}.$${string}`;
