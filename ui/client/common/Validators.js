import React from "react";

export const canNotBeEmpty = "This field cannot be empty"
export const duplicateValue = "This value is already taken"

export const notEmptyValidator = {
  isValid: (value) => !_.isEmpty(value),
  message: canNotBeEmpty,
  description: canNotBeEmpty
}

export function errorValidator(errors, fieldName) {
  const error = errors.find(error => error.fieldName === fieldName);
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

export function allValid(validators, values) {
  return validators.every(validator => validator.isValid(...values))
}

