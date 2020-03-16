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
  InvalidLiteralIntValue = "InvalidLiteralIntValue",
  InvalidPropertyFixedValue = "InvalidPropertyFixedValue",
}

/* eslint-disable i18next/no-literal-string */
export enum BackendValidator {
  MandatoryParameterValidator = "MandatoryParameterValidator",
  NotBlankParameterValidator = "NotBlankParameterValidator",
}

export enum ValidatorName {
  MandatoryParameterValidator = "MandatoryParameterValidator",
  ErrorValidator = "ErrorValidator",
  NotBlankParameterValidator = "NotBlankParameterValidator",
  LiteralIntValidator = "LiteralIntValidator",
  FixedValueValidator = "FixedValuesValidator",
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
  handledErrorType: error(errors, fieldName)?.typ ? HandledErrorType[error(errors, fieldName)?.typ] : "ErrorValidator",
  validatorType: ValidatorType.Backend,
})

export const mandatoryValueValidator: Validator = {
  isValid: value => !isEmpty(value),
  message: () => i18next.t("mandatoryValueValidator.message", "This field is mandatory and can not be empty"),
  description: () => i18next.t("validator.description", "Please fill field for this parameter"),
  handledErrorType: HandledErrorType.EmptyMandatoryParameterError,
  validatorType: ValidatorType.Frontend,
}

export const literalIntValidator: Validator = {
  isValid: value => !isNaN(value),
  message: () => i18next.t("literalIntValidator.message", "This value has to be an integer number"),
  description: () => i18next.t("literalIntValidator,description", "Please fill this field with an integer number"),
  handledErrorType: HandledErrorType.InvalidLiteralIntValue,
  validatorType: ValidatorType.Frontend,
}

export const fixedValueValidator = (possibleValues: Array<PossibleValue>): Validator => ({
  isValid: value => possibleValues.map(value => value.expression).includes(value),
  message: () => i18next.t("fixedValueValidator.message", "This value has to be one of values: ") + possibleValues.map(value => value.expression).join(","),
  description: () => i18next.t("fixedValueValidator.description", "Please choose one of available values"),
  handledErrorType: HandledErrorType.InvalidPropertyFixedValue,
  validatorType: ValidatorType.Frontend,
})

const blankStringLiteralPattern = new RegExp("'\\s*'")

export const notBlankValueValidator: Validator = {
  isValid: value => value != null && !blankStringLiteralPattern.test(value.trim()),
  message: () => i18next.t("notBlankValueValidator.message", "This field value is required and can not be blank"),
  description: () => i18next.t("validator.description", "Please fill field value for this parameter"),
  handledErrorType: HandledErrorType.BlankParameterError,
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

export const validators: Record<ValidatorName, (errors?: Array<Error>, fieldName?: string, possibleValues?: Array<PossibleValue>) => Validator> = {
  [ValidatorName.MandatoryParameterValidator]: () => mandatoryValueValidator,
  [ValidatorName.NotBlankParameterValidator]: () => notBlankValueValidator,
  [ValidatorName.ErrorValidator]: (errors, fieldName) => errorValidator(errors, fieldName),
  [ValidatorName.LiteralIntValidator]: () => literalIntValidator,
  [ValidatorName.FixedValueValidator]: (errors, fieldName, possibleValues) => fixedValueValidator(possibleValues),
}
