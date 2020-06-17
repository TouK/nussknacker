import {chain, isEmpty} from "lodash"
import i18next from "i18next"
import { loadSvgContent } from "../../../../common/LoaderUtils"
import { number } from "prop-types"

export enum ValidatorType {
  Frontend, Backend,
}

/* eslint-disable i18next/no-literal-string */
export enum HandledErrorType {
  EmptyMandatoryParameter = "EmptyMandatoryParameter",
  BlankParameter = "BlankParameter",
  WrongDateFormat = "WrongDateFormat",
  InvalidPropertyFixedValue = "InvalidPropertyFixedValue",
  InvalidIntegerLiteralParameter = "InvalidIntegerLiteralParameter",
  ErrorValidator = "ErrorValidator",
  MismatchParameter = "MismatchParameter",
  SmallerThanRequiredParameter = "SmallerThanRequiredParameter",
  GreaterThanRequiredParameter = "GreaterThanRequiredParameter"
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
}

export type Validator = {
  isValid: (...args: any[]) => boolean,
  message: () => string,
  description: () => string,
  handledErrorType: HandledErrorType,
  validatorType: ValidatorType,
  skipOnBlankIfNotRequired?: boolean,
  priority?: number
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
  validatorType: ValidatorType.Backend
})

export const mandatoryValueValidator = (skipOnBlankIfNotRequired: boolean = false, priority: number = Number.MAX_SAFE_INTEGER): Validator => ({
  isValid: value => !isEmpty(value),
  message: () => i18next.t("mandatoryValueValidator.message", "This field is mandatory and can not be empty"),
  description: () => i18next.t("validator.description", "Please fill field for this parameter"),
  handledErrorType: HandledErrorType.EmptyMandatoryParameter,
  validatorType: ValidatorType.Frontend,
  skipOnBlankIfNotRequired,
  priority
})

export const fixedValueValidator = (skipOnBlankIfNotRequired: boolean, priority: number) => (possibleValues: Array<PossibleValue>): Validator => ({
  isValid: value => possibleValues.map(pv => pv.expression).includes(value),
  message: () => i18next.t("fixedValueValidator.message", "This value has to be one of values: ") + possibleValues.map(value => value.expression).join(","),
  description: () => i18next.t("fixedValueValidator.description", "Please choose one of available values"),
  handledErrorType: HandledErrorType.InvalidPropertyFixedValue,
  validatorType: ValidatorType.Frontend,
  skipOnBlankIfNotRequired,
  priority
})

const literalRegExpPattern = (pattern: string) => new RegExp(pattern)

export const notBlankValueValidator = (skipOnBlankIfNotRequired: boolean, priority: number): Validator => ({
  isValid: value => !literalRegExpPattern("'\\s*'").test(value.trim()),
  message: () => i18next.t("notBlankValueValidator.message", "This field value is required and can not be blank"),
  description: () => i18next.t("validator.description", "Please fill field value for this parameter"),
  handledErrorType: HandledErrorType.BlankParameter,
  validatorType: ValidatorType.Frontend,
  skipOnBlankIfNotRequired,
  priority
})

export const regExpValueValidator = (skipOnBlankIfNotRequired: boolean, priority: number) => (pattern: string, message: string, description: string): Validator => ({
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || literalRegExpPattern(pattern).test(value.trim()),
  message: () => message,
  description: () => description,
  handledErrorType: HandledErrorType.MismatchParameter,
  validatorType: ValidatorType.Frontend,
  skipOnBlankIfNotRequired,
  priority
})

export const literalIntegerValueValidator = (skipOnBlankIfNotRequired: boolean = true, priority: number = 0): Validator => ({
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || literalRegExpPattern("^-?[0-9]+$").test(value),
  message: () => i18next.t("literalIntegerValueValidator.message", "This field value has to be an integer number"),
  description: () => i18next.t("literalIntegerValueValidator.description", "Please fill field by proper integer type"),
  handledErrorType: HandledErrorType.InvalidIntegerLiteralParameter,
  validatorType: ValidatorType.Frontend,
  skipOnBlankIfNotRequired,
  priority
})

export const minimalNumberValidator = (skipOnBlankIfNotRequired: boolean, priority: number) => (minimalNumber: number): Validator => ({
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || Number(value) >= minimalNumber,
  message: () => i18next.t("minimalNumberValidator.message", `This field value has to be a number greater than or equal to ${minimalNumber}`),
  description: () => i18next.t("minimalNumberValidator.description", "Please fill field by proper number"),
  handledErrorType: HandledErrorType.SmallerThanRequiredParameter,
  validatorType: ValidatorType.Frontend,
  skipOnBlankIfNotRequired,
  priority
})

export const maximalNumberValidator = (skipOnBlankIfNotRequired: boolean, priority: number) => (maximalNumber: number): Validator => ({
  //Blank value should be not validate - we want to chain validators
  isValid: value => isEmpty(value) || Number(value) <= maximalNumber,
  message: () => i18next.t("maximalNumberValidator.message", `This field value has to be a number lower than or equal to ${maximalNumber}`),
  description: () => i18next.t("maximalNumberValidator.description", "Please fill field by proper number"),
  handledErrorType: HandledErrorType.GreaterThanRequiredParameter,
  validatorType: ValidatorType.Frontend,
  skipOnBlankIfNotRequired,
  priority
})

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
  [BackendValidator.MandatoryParameterValidator]: ({skipOnBlankIfNotRequired, priority}) => mandatoryValueValidator(skipOnBlankIfNotRequired, priority),
  [BackendValidator.NotBlankParameterValidator]: ({skipOnBlankIfNotRequired, priority}) => notBlankValueValidator(skipOnBlankIfNotRequired, priority),
  [BackendValidator.LiteralIntegerValidator]: ({skipOnBlankIfNotRequired, priority}) => literalIntegerValueValidator(skipOnBlankIfNotRequired, priority),
  [BackendValidator.FixedValuesValidator]: ({possibleValues, skipOnBlankIfNotRequired, priority}) => fixedValueValidator(skipOnBlankIfNotRequired, priority)(possibleValues),
  [BackendValidator.RegExpParameterValidator]: ({pattern, message, description, skipOnBlankIfNotRequired, priority}) => regExpValueValidator(skipOnBlankIfNotRequired, priority)(pattern, message, description),
  [BackendValidator.MinimalNumberValidator]: ({minimalNumber, skipOnBlankIfNotRequired, priority}) => minimalNumberValidator(skipOnBlankIfNotRequired, priority)(minimalNumber),
  [BackendValidator.MaximalNumberValidator]: ({maximalNumber, skipOnBlankIfNotRequired, priority}) => maximalNumberValidator(skipOnBlankIfNotRequired, priority)(maximalNumber),
}
