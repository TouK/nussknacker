import { isNil } from "lodash";
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
    return isNil(item.valueEditor);
}

const permittedTypesMapping = {
    String: "java.lang.String",
    Boolean: "java.lang.Boolean",
    Long: "java.lang.Long",
    Integer: "java.lang.Integer",
};

const permittedTypes: string[] = Object.keys(permittedTypesMapping);

export function isPermittedTypeVariant(item: FragmentInputParameter): item is PermittedTypeParameterVariant {
    const clazzName = resolveRefClazzName(item.typ.refClazzName);
    return permittedTypes.includes(clazzName);
}

export function toFullRefClazzName(item: PermittedTypeParameterVariant): PermittedTypeParameterVariant {
    const refClazzName = item.typ.refClazzName;
    if (isPermittedTypeVariant(item) && permittedTypes.includes(refClazzName)) {
        // Internals like dictionary API require the full class name
        // In new fragments the param type is set to a simple name (e.g. String)
        // old fragments can still contain params in the full name format (e.g java.lang.String)
        const mappedClazzName = permittedTypesMapping[refClazzName];
        return { ...item, typ: { ...item.typ, refClazzName: mappedClazzName } } as PermittedTypeParameterVariant;
    }
    return item;
}

export type FieldName = `$param.${string}.$${string}`;
