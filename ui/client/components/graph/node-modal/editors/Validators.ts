import {chain, isEmpty} from "lodash"
import i18next from "i18next"

export enum ValidatorType {
  Frontend, Backend,
}

/* eslint-disable i18next/no-literal-string */
export enum HandledErrorType {
  EmptyMandatoryParameterError = "EmptyMandatoryParameterError",
  BlankParameterError = "BlankParameterError",
  WrongDateFormat = "WrongDateFormat",
}

/* eslint-disable i18next/no-literal-string */
export enum BackendValidator {
  MandatoryParameterValidator = "MandatoryParameterValidator",
  NotBlankParameterValidator = "NotBlankParameterValidator",
}

export type Validator = {
  isValid: (...args: any[]) => boolean,
  message: () => string,
  description: () => string,
  handledErrorType: HandledErrorType,
  validatorType: ValidatorType,
}

export type Error = {
  fieldName: string,
  message: string,
  description: string,
  typ: string,
}

const error = (errors: Array<Error>, fieldName: string): Error =>
  errors && errors.find(error => error.fieldName === fieldName || error.fieldName === `$${fieldName}`)

export const errorValidator = (errors: Array<Error>, fieldName: string): Validator => ({
  isValid: () => !error(errors, fieldName),
  message: () => error(errors, fieldName)?.message,
  description: () => error(errors, fieldName)?.description,
  handledErrorType: HandledErrorType[error(errors, fieldName)?.typ],
  validatorType: ValidatorType.Backend,
})

export const mandatoryValueValidator: Validator = {
  isValid: value => !isEmpty(value),
  message: () => i18next.t("mandatoryValueValidator.message", "This field is mandatory and can not be empty"),
  description: () => i18next.t("validator.description", "Please fill field for this parameter"),
  handledErrorType: HandledErrorType.EmptyMandatoryParameterError,
  validatorType: ValidatorType.Frontend,
}

const blankStringLiteralPattern = new RegExp("'\\s*'")

export const notBlankValueValidator: Validator = {
  isValid: value => value != null && !blankStringLiteralPattern.test(value.trim()),
  message: () => i18next.t("notBlankValueValidator.message", "This field value is required and can not be blank"),
  description: () => i18next.t("validator.description", "Please fill field value for this parameter"),
  handledErrorType: HandledErrorType.BlankParameterError,
  validatorType: ValidatorType.Frontend,
}

export function withoutDuplications(validators: Array<Validator>): Array<Validator> {
  return chain(validators)
    .groupBy(validator => validator.handledErrorType)
    .map((value, key) => chain(value).sortBy(validator => validator.validatorType).head().value())
    .value()
}

export function allValid(validators: Array<Validator>, values: Array<string>): boolean {
  return withoutDuplications(validators).every(validator => validator.isValid(...values))
}

//Mapping BE Validator to FE Validator
export const validators: Record<BackendValidator, Validator> = {
  [BackendValidator.MandatoryParameterValidator]: mandatoryValueValidator,
  [BackendValidator.NotBlankParameterValidator]: notBlankValueValidator,
}
