// @flow
import React from "react";
import _ from "lodash";

export type Validator = {
    isValid: any => boolean,
    message: ?string,
    description: ?string
}

export const canNotBeEmpty = "This field cannot be empty"
export const duplicateValue = "This value is already taken"

export const notEmptyValidator: Validator = {
  isValid: value => !_.isEmpty(value),
  message: canNotBeEmpty,
  description: canNotBeEmpty
}

export function errorValidator(errors: Array<Object>, fieldName: string): Validator {
  const error = errors.find(error => error.fieldName === fieldName)
  return error ?
    {
      isValid: _ => false,
      message: error.message,
      description: error.description
    }
    :
    {
      isValid: _ => true,
      message: null,
      description: null
    }
}

export function allValid(validators: Array<Validator>, values: Array<any>): boolean {
  return validators.every(validator => validator.isValid(...values))
}

