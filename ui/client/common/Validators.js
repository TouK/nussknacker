import React from "react";

export const canNotBeEmpty = "This field cannot be empty"
export const duplicateValue = "This value is already taken"

export const notEmptyValidator = {
  isValid: (value) => !_.isEmpty(value),
  message: canNotBeEmpty
}

