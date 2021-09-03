import {chain, isEmpty} from "lodash"
import i18next from "i18next"

export enum ValidatorType {
  Frontend, Backend,
}

/* eslint-disable i18next/no-literal-string */
export enum HandledErrorType {
  AlreadyExists = "AlreadyExists",
  EmptyMandatoryParameter = "EmptyMandatoryParameter",
  BlankParameter = "BlankParameter",
  WrongDateFormat = "WrongDateFormat",
  InvalidPropertyFixedValue = "InvalidPropertyFixedValue",
  InvalidIntegerLiteralParameter = "InvalidIntegerLiteralParameter",
  ErrorValidator = "ErrorValidator",
  MismatchParameter = "MismatchParameter",
  SmallerThanRequiredParameter = "SmallerThanRequiredParameter",
  GreaterThanRequiredParameter = "GreaterThanRequiredParameter",
  JsonRequiredParameter = "JsonRequiredParameter",
}

/* eslint-disable i18next/no-literal-string */
export enum BackendValidator {
  MandatoryParameterValidator = "MandatoryParameterValidator",
  NotBlankParameterValidator = "NotBlankParameterValidator",
  FixedValuesValidator = "FixedValuesValidator",
  RegExpParameterValidator = "RegExpParameterValidator",
  LiteralIntegerValidator = "LiteralIntegerValidator",
  MinimalNumberValidator = "MinimalNumberValidator",
  MaximalNumberValidator = "MaximalNumberValidator",
  JsonValidator = "JsonValidator",
}

export type Validator = {
  isValid: (...args: any[]) => boolean,
  message: () => string,
  description: () => string,
  handledErrorType: HandledErrorType,
  validatorType: ValidatorType,
}

export type PossibleValue = {
  expression: string,
  label: string,
}

export type Error = {
  fieldName: string,
  message: string,
  description: string,
  typ: string,
}

const error = (errors: Array<Error>, fieldName: string): Error => errors && errors.find(error => error.fieldName === fieldName || error.fieldName === `$${fieldName}`)

export const errorValidator = (errors: Array<Error>, fieldName: string): Validator => ({
  isValid: () => !error(errors, fieldName),
  message: () => error(errors, fieldName)?.message,
  description: () => error(errors, fieldName)?.description,
  handledErrorType: error(errors, fieldName)?.typ ? HandledErrorType[error(errors, fieldName)?.typ] : HandledErrorType.ErrorValidator,
  validatorType: ValidatorType.Backend,
})

export const mandatoryValueValidator: Validator = {
  isValid: value => !isEmpty(value),
  message: () => i18next.t("mandatoryValueValidator.message", "This field is mandatory and can not be empty"),
  description: () => i18next.t("validator.mandatory.description", "Please fill field for this parameter"),
  handledErrorType: HandledErrorType.EmptyMandatoryParameter,
  validatorType: ValidatorType.Frontend,
}

export const fixedValueValidator = (possibleValues: Array<PossibleValue>): Validator => ({
  isValid: value => possibleValues.map(pv => pv.expression).includes(value),
  message: () => i18next.t("fixedValueValidator.message", "This value has to be one of values: ") + possibleValues.map(value => value.expression).join(","),
  description: () => i18next.t("Validator.fixed.description", "Please choose one of available values"),
  handledErrorType: HandledErrorType.InvalidPropertyFixedValue,
  validatorType: ValidatorType.Frontend,
})

const literalRegExpPattern = (pattern: string) => new RegExp(pattern)

export const notBlankValueValidator: Validator = {
  isValid: value => !literalRegExpPattern("'\\s*'").test(value.trim()),
  message: () => i18next.t("notBlankValueValidator.message", "This field value is required and can not be blank"),
  description: () => i18next.t("validator.notBlank.description", "Please fill field value for this parameter"),
  handledErrorType: HandledErrorType.BlankParameter,
  validatorType: ValidatorType.Frontend,
}

export const regExpValueValidator = (pattern: string, message: string, description: string): Validator => ({
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || literalRegExpPattern(pattern).test(value.trim()),
  message: () => message,
  description: () => description,
  handledErrorType: HandledErrorType.MismatchParameter,
  validatorType: ValidatorType.Frontend,
})

export const literalIntegerValueValidator: Validator = {
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || literalRegExpPattern("^-?[0-9]+$").test(value),
  message: () => i18next.t("literalIntegerValueValidator.message", "This field value has to be an integer number"),
  description: () => i18next.t("literalIntegerValueValidator.description", "Please fill field by proper integer type"),
  handledErrorType: HandledErrorType.InvalidIntegerLiteralParameter,
  validatorType: ValidatorType.Frontend,
}

//It's kind of hack.. Because from SPeL we get string with "L" or others number's mark.
//We can't properly cast that kind of string to number, so we have to remove all not digits chars.
const normalizeStringToNumber = (value: string): string => {
  return value.replace(/[^-?\d.]/g, "")
}

export const minimalNumberValidator = (minimalNumber: number): Validator => ({
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || Number(normalizeStringToNumber(value)) >= minimalNumber,
  message: () => i18next.t("minNumberValidator.message", "This field value has to be a number lesser than or equal to {{min}}", {min: minimalNumber}),
  description: () => i18next.t("minNumberValidator.description", "Please fill field by proper number"),
  handledErrorType: HandledErrorType.SmallerThanRequiredParameter,
  validatorType: ValidatorType.Frontend,
})

export const maximalNumberValidator = (maximalNumber: number): Validator => ({
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || Number(normalizeStringToNumber(value)) <= maximalNumber,
  message: () => i18next.t("maxNumberValidator.message", "This field value has to be a number lesser than or equal to {{max}}", {max: maximalNumber}),
  description: () => i18next.t("maxNumberValidator.description", "Please fill field by proper number"),
  handledErrorType: HandledErrorType.GreaterThanRequiredParameter,
  validatorType: ValidatorType.Frontend,
})

const isJsonValid = (value: string): boolean => {
  const trimmedValue = value
    .replace(/^'/, "")
    .replace(/'$/, "")

  try {
    JSON.parse(trimmedValue)
  } catch (e) {
    return false
  }

  return true
}

export const jsonValidator: Validator = {
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || isJsonValid(value),
  message: () => i18next.t("jsonValidator.message", `This field value has to be a valid json`),
  description: () => i18next.t("jsonValidator.description", "Please fill field with valid json"),
  handledErrorType: HandledErrorType.JsonRequiredParameter,
  validatorType: ValidatorType.Frontend,
}

export function withoutDuplications(validators: Array<Validator>): Array<Validator> {
  return isEmpty(validators) ? [] :
    chain(validators)
      .groupBy(validator => validator.handledErrorType)
      .map((value, key) => chain(value).sortBy(validator => validator.validatorType).head().value())
      .value()
}

export function allValid(validators: Array<Validator>, values: Array<string>): boolean {
  return withoutDuplications(validators).every(validator => validator.isValid(...values))
}

export const validators: Record<BackendValidator, (...args: any[]) => Validator> = {
  [BackendValidator.MandatoryParameterValidator]: () => mandatoryValueValidator,
  [BackendValidator.NotBlankParameterValidator]: () => notBlankValueValidator,
  [BackendValidator.LiteralIntegerValidator]: () => literalIntegerValueValidator,
  [BackendValidator.FixedValuesValidator]: ({possibleValues}) => fixedValueValidator(possibleValues),
  [BackendValidator.RegExpParameterValidator]: ({pattern, message, description}) => regExpValueValidator(pattern, message, description),
  [BackendValidator.MinimalNumberValidator]: ({minimalNumber}) => minimalNumberValidator(minimalNumber),
  [BackendValidator.MaximalNumberValidator]: ({maximalNumber}) => maximalNumberValidator(maximalNumber),
  [BackendValidator.JsonValidator]: () => jsonValidator,
}
