import React from "react";

export const canNotBeEmpty = "This field cannot be empty"
export const duplicate = (value) => value + " already taken"

export const notEmptyValidator = {
  isValid: (value) => !_.isEmpty(value),
  message: canNotBeEmpty
}

