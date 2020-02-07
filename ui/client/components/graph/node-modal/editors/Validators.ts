import _ from "lodash"

export enum validatorType {
  MANDATORY_VALUE_VALIDATOR = "MandatoryValueValidator",
  ERROR_VALIDATOR = "ErrorValidator",
}

export type Validator = {
  isValid: (...args: any[]) => boolean
  message: string
  description: string
}

export type Error = {
  fieldName: string,
  message: string,
  description: string,
}

const error = (errors: Array<Error>, fieldName: string) =>
  errors && errors.find(error => error.fieldName === fieldName || error.fieldName === `$${fieldName}`)

export const errorValidator = (errors: Array<Error>, fieldName: string) => ({
  isValid: () => !error(errors, fieldName),
  message: error(errors, fieldName)?.message,
  description: error(errors, fieldName)?.description,
})

export const mandatoryValueValidator: Validator = {
  isValid: value => !_.isEmpty(value),
  message: "This field cannot be empty",
  description: "This field cannot be empty",
}

export function allValid(validators: Array<Validator>, values: Array<any>): boolean {
  return validators.every(validator => validator.isValid(...values))
}

export const validators: Record<validatorType, (errors?: Array<Error>, fieldName?: string) => Validator> = {
  [validatorType.MANDATORY_VALUE_VALIDATOR]: () => mandatoryValueValidator,
  [validatorType.ERROR_VALIDATOR]: (errors, fieldName) => errorValidator(errors, fieldName),
}
