import {chain, isEmpty} from "lodash"

export enum ValidatorType {
  Frontend, Backend,
}

export enum HandledErrorType {
  EmptyMandatoryParameter = "EmptyMandatoryParameter",
  WrongDateFormat = "WrongDateFormat",
}

export enum ValidatorName {
  MandatoryValueValidator = "MandatoryValueValidator",
  ErrorValidator = "ErrorValidator",
}

export type Validator = {
  isValid: (...args: any[]) => boolean,
  message: string,
  description: string,
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
  message: error(errors, fieldName)?.message,
  description: error(errors, fieldName)?.description,
  handledErrorType: HandledErrorType[error(errors, fieldName)?.typ],
  validatorType: ValidatorType.Backend,
})

export const mandatoryValueValidator: Validator = {
  isValid: value => !isEmpty(value),
  message: "This field cannot be empty",
  description: "This field cannot be empty",
  handledErrorType: HandledErrorType.EmptyMandatoryParameter,
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

export const validators: Record<ValidatorName, (errors?: Array<Error>, fieldName?: string) => Validator> = {
  [ValidatorName.MandatoryValueValidator]: () => mandatoryValueValidator,
  [ValidatorName.ErrorValidator]: (errors, fieldName) => errorValidator(errors, fieldName),
}
