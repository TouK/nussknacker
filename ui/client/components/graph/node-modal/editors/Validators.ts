import {chain, isEmpty} from "lodash"

export enum ValidatorType {
  Frontend, Backend,
}

export enum HandledErrorType {
  EmptyMandatoryParameter = "EmptyMandatoryParameter",
  WrongDateFormat = "WrongDateFormat",
  InvalidLiteralIntValue = "InvalidLiteralIntValue",
  InvalidPropertyFixedValue = "InvalidPropertyFixedValue",
}

export enum ValidatorName {
  MandatoryValueValidator = "MandatoryValueValidator",
  ErrorValidator = "ErrorValidator",
  LiteralIntValidator = "LiteralIntValidator",
  FixedValueValidator = "FixedValuesValidator",
}

export type Validator = {
  isValid: (...args: any[]) => boolean,
  message: string,
  description: string,
  handledErrorType: HandledErrorType,
  validatorType: ValidatorType,
}

export type PossibleValue = {
  expression: string,
  label: string,
}

export type Error = {
  fieldName: string,
  message: string,
  description: string,
  typ: string,
}

const error = (errors: Array<Error>, fieldName: string): Error => errors && errors.find(error => error.fieldName === fieldName || error.fieldName === `$${fieldName}`)

export const errorValidator = (errors: Array<Error>, fieldName: string): Validator => ({
  isValid: () => !error(errors, fieldName),
  message: error(errors, fieldName)?.message,
  description: error(errors, fieldName)?.description,
  handledErrorType: error(errors, fieldName)?.typ ? HandledErrorType[error(errors, fieldName)?.typ] : "ErrorValidator",
  validatorType: ValidatorType.Backend,
})

export const mandatoryValueValidator: Validator = {
  isValid: value => !isEmpty(value),
  message: "This field cannot be empty",
  description: "This field cannot be empty",
  handledErrorType: HandledErrorType.EmptyMandatoryParameter,
  validatorType: ValidatorType.Frontend,
}

export const literalIntValidator: Validator = {
  isValid: value => !isNaN(value),
  message: "This value has to be an integer number",
  description: "Please fill this field with an integer number",
  handledErrorType: HandledErrorType.InvalidLiteralIntValue,
  validatorType: ValidatorType.Frontend,
}

export const fixedValueValidator = (possibleValues: Array<PossibleValue>): Validator => ({
  isValid: value => possibleValues.map(value => value.expression).includes(value),
  message: `This value has to be one of values: ${possibleValues.toString()}`,
  description: "Please choose one of available values",
  handledErrorType: HandledErrorType.InvalidLiteralIntValue,
  validatorType: ValidatorType.Frontend,
})

export function withoutDuplications(validators: Array<Validator>): Array<Validator> {
  return isEmpty(validators) ? [] :
    chain(validators)
      .groupBy(validator => validator.handledErrorType)
      .map((value, key) => chain(value).sortBy(validator => validator.validatorType).head().value())
      .value()
}

export function allValid(validators: Array<Validator>, values: Array<string>): boolean {
  return withoutDuplications(validators).every(validator => validator.isValid(...values))
}

export const validators: Record<ValidatorName, (errors?: Array<Error>, fieldName?: string, possibleValues?: Array<PossibleValue>) => Validator> = {
  [ValidatorName.MandatoryValueValidator]: () => mandatoryValueValidator,
  [ValidatorName.ErrorValidator]: (errors, fieldName) => errorValidator(errors, fieldName),
  [ValidatorName.LiteralIntValidator]: () => literalIntValidator,
  [ValidatorName.FixedValueValidator]: (errors, fieldName, possibleValues) => fixedValueValidator(possibleValues),
}
