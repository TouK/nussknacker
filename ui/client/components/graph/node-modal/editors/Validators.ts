import _ from "lodash"

export enum validatorType {
  NOT_EMPTY_VALIDATOR = "NotEmptyValidator",
  ERROR_VALIDATOR = "ErrorValidator",
}

export type Validator = {
  isValid: (...args: any[]) => boolean
  message: string
  description: string
}

export const validators: Record<validatorType, Function> = {
  [validatorType.NOT_EMPTY_VALIDATOR]: () => notEmptyValidator,
  [validatorType.ERROR_VALIDATOR]: (errors, fieldName) => errorValidator(errors, fieldName),
}

export const errorValidator = (errors, fieldName) => ({
  isValid: () => !error(errors, fieldName),
  message: error(errors, fieldName)?.message,
  description: error(errors, fieldName)?.description,
})

const error = (errors, fieldName) => errors && errors.find(error => error.fieldName === fieldName || error.fieldName === `$${fieldName}`)

export const notEmptyValidator: Validator = {
  isValid: value => !_.isEmpty(value),
  message: "This field cannot be empty",
  description: "This field cannot be empty",
}

export function allValid(validators: Array<Validator>, values: Array<any>): boolean {
  return validators.every(validator => validator.isValid(...values))
}
