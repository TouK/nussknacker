import i18next from "i18next"
import _ from "lodash"

export type Validator = {
    isValid: (...args: any[]) => boolean;
    message: string;
    description: string;
}

type ErrorType = {
  fieldName: string;
  message: string;
  description: string;
}

export const canNotBeEmpty = i18next.t("validation.canNotBeEmpty","This field cannot be empty")
export const duplicateValue = i18next.t("validation.duplicateValue","This value is already taken")

export const notEmptyValidator: Validator = {
  isValid: value => !_.isEmpty(value),
  message: canNotBeEmpty,
  description: canNotBeEmpty,
}

export function errorValidator(errors: Array<ErrorType>, fieldName: string): Validator {
  const error = errors.find(error => error.fieldName === fieldName)
  return error ?
    {
      isValid: () => false,
      message: error.message,
      description: error.description,
    }
    :
    {
      isValid: () => true,
      message: null,
      description: null,
    }
}

export function allValid(validators: Array<Validator>, values: Array<any>): boolean {
  return validators.every(validator => validator.isValid(...values))
}
