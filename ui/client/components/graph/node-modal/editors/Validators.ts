import _ from "lodash"

export enum validatorType {
  FRONT_END, BACKEND,
}

export enum handledErrorType {
  EMPTY_MANDATORY_PARAMETER = "EmptyMandatoryParameter",
  WRONG_DATE_FORMAT = "WrongDateFormat",
}

export enum validatorName {
  MANDATORY_VALUE_VALIDATOR = "MandatoryValueValidator",
  ERROR_VALIDATOR = "ErrorValidator",
}

export type Validator = {
  isValid: (...args: any[]) => boolean,
  message: string,
  description: string,
  handledErrorType: handledErrorType,
  validatorType: validatorType,
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
  handledErrorType: handledErrorType[error(errors, fieldName)?.typ],
  validatorType: validatorType.BACKEND,
})

export const mandatoryValueValidator: Validator = {
  isValid: value => !_.isEmpty(value),
  message: "This field cannot be empty",
  description: "This field cannot be empty",
  handledErrorType: handledErrorType.EMPTY_MANDATORY_PARAMETER,
  validatorType: validatorType.FRONT_END,
}

export function withoutDuplications(validators: Array<Validator>): Array<Validator> {
  return _(validators)
    .groupBy(validator => validator.handledErrorType)
    .map((value, key) => _.chain(value).sortBy(validator => validator.validatorType).head().value())
    .value()
}

export function allValid(validators: Array<Validator>, values: Array<string>): boolean {
  return withoutDuplications(validators).every(validator => validator.isValid(...values))
}

export const validators: Record<validatorName, (errors?: Array<Error>, fieldName?: string) => Validator> = {
  [validatorName.MANDATORY_VALUE_VALIDATOR]: () => mandatoryValueValidator,
  [validatorName.ERROR_VALIDATOR]: (errors, fieldName) => errorValidator(errors, fieldName),
}
