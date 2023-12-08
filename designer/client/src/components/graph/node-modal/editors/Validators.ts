import { chain, isEmpty } from "lodash";
import i18next from "i18next";
import { NodeValidationError } from "../../../../types";
import { FormatterType } from "./expression/Formatter";

export enum ValidatorType {
    Frontend,
    Backend,
}

/* eslint-disable i18next/no-literal-string */
export enum HandledErrorType {
    AlreadyExists = "AlreadyExists",
    EmptyMandatoryParameter = "EmptyMandatoryParameter",
    UniqueParameter = "UniqueParameter",
    BlankParameter = "BlankParameter",
    WrongDateFormat = "WrongDateFormat",
    InvalidPropertyFixedValue = "InvalidPropertyFixedValue",
    InvalidIntegerLiteralParameter = "InvalidIntegerLiteralParameter",
    ErrorValidator = "ErrorValidator",
    MismatchParameter = "MismatchParameter",
    LowerThanRequiredParameter = "LowerThanRequiredParameter",
    GreaterThanRequiredParameter = "GreaterThanRequiredParameter",
    JsonRequiredParameter = "JsonRequiredParameter",
}

export type Validator = {
    isValid: (...args: any[]) => boolean;
    message: () => string;
    description: () => string;
    handledErrorType: HandledErrorType;
    validatorType: ValidatorType;
};

export type PossibleValue = {
    expression: string;
    label: string;
};

export type PosibleValues = {
    expression: PossibleValue;
    label: PossibleValue;
};

export interface Error {
    fieldName: string;
    message: string;
    description: string;
    typ: string;
}

export const errorValidator = (errors: Error[], fieldName: string): Validator => {
    const error = errors?.find((error) => error.fieldName === fieldName || error.fieldName === `$${fieldName}`);
    return {
        isValid: () => !error,
        message: () => error?.message,
        description: () => error?.description,
        handledErrorType: error?.typ ? HandledErrorType[error?.typ] : HandledErrorType.ErrorValidator,
        validatorType: ValidatorType.Backend,
    };
};

export const mandatoryValueValidator: Validator = {
    isValid: (value) => !isEmpty(value),
    message: () => i18next.t("mandatoryValueValidator.message", "This field is mandatory and can not be empty"),
    description: () => i18next.t("validator.mandatory.description", "Please fill field for this parameter"),
    handledErrorType: HandledErrorType.EmptyMandatoryParameter,
    validatorType: ValidatorType.Frontend,
};

export const uniqueValueValidator: (otherValues: string[]) => Validator = (otherValues) => ({
    isValid: (value) => !otherValues.includes(value),
    message: () => i18next.t("uniqueValueValidator.message", "This field has to be unique"),
    description: () => i18next.t("validator.unique.description", "Please fill field with unique value"),
    handledErrorType: HandledErrorType.UniqueParameter,
    validatorType: ValidatorType.Frontend,
});
export const uniqueListValueValidator = (list: string[], currentIndex: number): Validator => ({
    ...uniqueValueValidator(list.filter((v, i) => i !== currentIndex)),
    message: () => i18next.t("uniqueListValueValidator.message", "This field has to be unique across list"),
});
export const uniqueScenarioValueValidator: typeof uniqueValueValidator = (otherValues) => ({
    ...uniqueValueValidator(otherValues),
    message: () => i18next.t("uniqueScenarioValueValidator.message", "This field has to be unique across scenario"),
});

const regExpPattern = (pattern: string) => new RegExp(pattern);

export const notBlankValueValidator: Validator = {
    isValid: (value) => !regExpPattern("^['\"]\\s*['\"]$").test(value.trim()),
    message: () => i18next.t("notBlankValueValidator.message", "This field value is required and can not be blank"),
    description: () => i18next.t("validator.notBlank.description", "Please fill field value for this parameter"),
    handledErrorType: HandledErrorType.BlankParameter,
    validatorType: ValidatorType.Frontend,
};

export const literalIntegerValueValidator: Validator = {
    //Blank value should be not validate - we want to chain validators
    isValid: (value) => isEmpty(value) || regExpPattern("^-?[0-9]+$").test(value),
    message: () => i18next.t("literalIntegerValueValidator.message", "This field value has to be an integer number"),
    description: () => i18next.t("literalIntegerValueValidator.description", "Please fill field by proper integer type"),
    handledErrorType: HandledErrorType.InvalidIntegerLiteralParameter,
    validatorType: ValidatorType.Frontend,
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
    validatorType: ValidatorType.Frontend,
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
    validatorType: ValidatorType.Frontend,
});

export function withoutDuplications(validators: Array<Validator>): Array<Validator> {
    return isEmpty(validators)
        ? []
        : chain(validators)
              .groupBy((validator) => validator.handledErrorType)
              .map((value, key) =>
                  chain(value)
                      .sortBy((validator) => validator.validatorType)
                      .head()
                      .value(),
              )
              .value();
}

export function allValid(validators: Array<Validator>, values: Array<string>): boolean {
    return withoutDuplications(validators).every((validator) => validator.isValid(...values));
}

export type FieldError = Pick<Error, "message" | "description"> | undefined;

export const getValidationErrorForField = (errors: NodeValidationError[], fieldName: string) => {
    const validator = errorValidator(errors, fieldName);

    const fieldError: FieldError = !validator.isValid()
        ? {
              message: validator.message && validator.message(),
              description: validator.description && validator.description(),
          }
        : undefined;
    return fieldError;
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
