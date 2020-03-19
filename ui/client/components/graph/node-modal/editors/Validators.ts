import {chain, isEmpty} from "lodash"
import i18next from "i18next"

export enum ValidatorType {
  Frontend, Backend,
}

/* eslint-disable i18next/no-literal-string */
export enum HandledErrorType {
  EmptyMandatoryParameter = "EmptyMandatoryParameter",
  BlankParameter = "BlankParameter",
  WrongDateFormat = "WrongDateFormat",
  InvalidPropertyFixedValue = "InvalidPropertyFixedValue",
  ErrorValidator = "ErrorValidator",
  NotMatchParameter = "NotMatchParameter",
}

/* eslint-disable i18next/no-literal-string */
export enum BackendValidator {
  MandatoryParameterValidator = "MandatoryParameterValidator",
  NotBlankParameterValidator = "NotBlankParameterValidator",
  FixedValuesValidator = "FixedValuesValidator",
  RegExpValidator = "RegExpValidator",
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
  description: () => i18next.t("validator.description", "Please fill field for this parameter"),
  handledErrorType: HandledErrorType.EmptyMandatoryParameter,
  validatorType: ValidatorType.Frontend,
}

export const fixedValueValidator = (possibleValues: Array<PossibleValue>): Validator => ({
  isValid: value => possibleValues.map(pv => pv.expression).includes(value),
  message: () => i18next.t("fixedValueValidator.message", "This value has to be one of values: ") + possibleValues.map(value => value.expression).join(","),
  description: () => i18next.t("fixedValueValidator.description", "Please choose one of available values"),
  handledErrorType: HandledErrorType.InvalidPropertyFixedValue,
  validatorType: ValidatorType.Frontend,
})

const literalRegExpPattern = (pattern: string) => new RegExp(pattern)

export const notBlankValueValidator: Validator = {
  isValid: value => !isEmpty(value) && !literalRegExpPattern("'\\s*'").test(value.trim()),
  message: () => i18next.t("notBlankValueValidator.message", "This field value is required and can not be blank"),
  description: () => i18next.t("validator.description", "Please fill field value for this parameter"),
  handledErrorType: HandledErrorType.BlankParameter,
  validatorType: ValidatorType.Frontend,
}

export const regExpValueValidator = (pattern: string, message: string, description: string): Validator => ({
  //Empty value should be not validate - we want to chain validators
  isValid: value => !notBlankValueValidator.isValid(value) || literalRegExpPattern(pattern).test(value.trim()),
  message: () => message,
  description: () => description,
  handledErrorType: HandledErrorType.NotMatchParameter,
  validatorType: ValidatorType.Frontend,
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
  [BackendValidator.MandatoryParameterValidator]: () => mandatoryValueValidator,
  [BackendValidator.NotBlankParameterValidator]: () => notBlankValueValidator,
  [BackendValidator.FixedValuesValidator]: ({possibleValues}) => fixedValueValidator(possibleValues),
  [BackendValidator.RegExpValidator]: ({pattern, message, description}) => regExpValueValidator(pattern, message, description),
}
