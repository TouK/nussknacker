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

const error = (errors, fieldName) => errors && errors.find(error => error.fieldName === fieldName || error.fieldName === `$${fieldName}`)

export const errorValidator = (errors, fieldName) => ({
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

export const validators: Record<validatorType, Function> = {
  [validatorType.MANDATORY_VALUE_VALIDATOR]: () => mandatoryValueValidator,
  [validatorType.ERROR_VALIDATOR]: (errors, fieldName) => errorValidator(errors, fieldName),
}
