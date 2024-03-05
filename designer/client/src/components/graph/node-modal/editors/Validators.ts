import { isEmpty } from "lodash";
import i18next from "i18next";
import { NodeValidationError } from "../../../../types";
import { FormatterType } from "./expression/Formatter";

export enum HandledErrorType {
    AlreadyExists = "AlreadyExists",
    EmptyMandatoryParameter = "EmptyMandatoryParameter",
    UniqueParameter = "UniqueParameter",
    InvalidIntegerLiteralParameter = "InvalidIntegerLiteralParameter",
    LowerThanRequiredParameter = "LowerThanRequiredParameter",
    GreaterThanRequiredParameter = "GreaterThanRequiredParameter",
}

export type Validator = {
    isValid: (...args: any[]) => boolean;
    message: () => string;
    description: () => string;
    handledErrorType: HandledErrorType;
};

export type PossibleValue = {
    expression: string;
    label: string;
};

export const mandatoryValueValidator: Validator = {
    isValid: (value) => !isEmpty(value),
    message: () => i18next.t("mandatoryValueValidator.message", "This field is mandatory and can not be empty"),
    description: () => i18next.t("validator.mandatory.description", "Please fill field for this parameter"),
    handledErrorType: HandledErrorType.EmptyMandatoryParameter,
};

export const uniqueValueValidator: (otherValues: string[]) => Validator = (otherValues) => ({
    isValid: (value) => !otherValues.includes(value),
    message: () => i18next.t("uniqueValueValidator.message", "This field has to be unique"),
    description: () => i18next.t("validator.unique.description", "Please fill field with unique value"),
    handledErrorType: HandledErrorType.UniqueParameter,
});
export const uniqueScenarioValueValidator: typeof uniqueValueValidator = (otherValues) => ({
    ...uniqueValueValidator(otherValues),
    message: () => i18next.t("uniqueScenarioValueValidator.message", "This field has to be unique across scenario"),
});

const regExpPattern = (pattern: string) => new RegExp(pattern);

export const literalIntegerValueValidator: Validator = {
    //Blank value should be not validate - we want to chain validators
    isValid: (value) => isEmpty(value) || regExpPattern("^-?[0-9]+$").test(value),
    message: () => i18next.t("literalIntegerValueValidator.message", "This field value has to be an integer number"),
    description: () => i18next.t("literalIntegerValueValidator.description", "Please fill field by proper integer type"),
    handledErrorType: HandledErrorType.InvalidIntegerLiteralParameter,
};

//It's kind of hack.. Because from SPeL we get string with "L" or others number's mark.
//We can't properly cast that kind of string to number, so we have to remove all not digits chars.
const normalizeStringToNumber = (value: string): string => {
    return value.replace(/[^-?\d.]/g, "");
};

export const minimalNumberValidator = (minimalNumber: number): Validator => ({
    //Blank value should be not validate - we want to chain validators
    isValid: (value) => isEmpty(value) || Number(normalizeStringToNumber(value)) >= minimalNumber,
    message: () =>
        i18next.t("minNumberValidator.message", "This field value has to be a number greater than or equal to {{min}}", {
            min: minimalNumber,
        }),
    description: () => i18next.t("minNumberValidator.description", "Please fill field by proper number"),
    handledErrorType: HandledErrorType.LowerThanRequiredParameter,
});

export const maximalNumberValidator = (maximalNumber: number): Validator => ({
    //Blank value should be not validate - we want to chain validators
    isValid: (value) => isEmpty(value) || Number(normalizeStringToNumber(value)) <= maximalNumber,
    message: () =>
        i18next.t("maxNumberValidator.message", "This field value has to be a number lesser than or equal to {{max}}", {
            max: maximalNumber,
        }),
    description: () => i18next.t("maxNumberValidator.description", "Please fill field by proper number"),
    handledErrorType: HandledErrorType.GreaterThanRequiredParameter,
});

export type FieldError = Pick<NodeValidationError, "message" | "description">;

export const getValidationErrorsForField = (errors: NodeValidationError[], fieldName: string) => {
    const fieldErrors: FieldError[] = errors
        .filter((error) => error.fieldName === fieldName)
        .map(({ message, description }) => ({
            message,
            description,
        }));
    return fieldErrors;
};

export const extendErrors = (
    errors: NodeValidationError[],
    value: string,
    fieldName: string,
    validators: Validator[],
): NodeValidationError[] => {
    const customValidatorErrors: NodeValidationError[] = validators
        .filter((validator) => !validator.isValid(value))
        .map((validator) => ({
            errorType: "SaveAllowed",
            fieldName,
            typ: FormatterType.String,
            description: validator.description(),
            message: validator.message(),
        }));

    return [...errors, ...customValidatorErrors];
};
