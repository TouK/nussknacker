import {chain, isEmpty} from "lodash"
import i18next from "i18next"

export enum ValidatorType {
  Frontend, Backend,
}

export enum HandledErrorType {
  MandatoryParameterValidator = "MandatoryParameterValidator",
  NotBlankParameterValidator = "NotBlankParameterValidator",
  WrongDateFormat = "WrongDateFormat",
  ErrorValidator = "ErrorValidator",
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
  message: () => i18next.t("mandatoryValueValidator.message", "Parameter expression is mandatory"),
  description: () => i18next.t("validator.description", "Please fill expression for this parameter"),
  handledErrorType: HandledErrorType.MandatoryParameterValidator,
  validatorType: ValidatorType.Frontend,
}

const blankStringLiteralPattern = new RegExp("'\\s*'")

export const notBlankValueValidator: Validator = {
  isValid: value => value != null && !blankStringLiteralPattern.test(value.trim()),
  message: () => i18next.t("mandatoryValueValidator.message", "Parameter expression can't be blank"),
  description: () => i18next.t("validator.description", "Please fill expression for this parameter"),
  handledErrorType: HandledErrorType.NotBlankParameterValidator,
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

export const validators: Record<HandledErrorType, (errors?: Array<Error>, fieldName?: string) => Validator> = {
  [HandledErrorType.MandatoryParameterValidator]: () => mandatoryValueValidator,
  [HandledErrorType.NotBlankParameterValidator]: () => notBlankValueValidator,
  [HandledErrorType.ErrorValidator]: (errors, fieldName) => errorValidator(errors, fieldName),
  [HandledErrorType.WrongDateFormat]: (errors, fieldName) => errorValidator(errors, fieldName),
}
