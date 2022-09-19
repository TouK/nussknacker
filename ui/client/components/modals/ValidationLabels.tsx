import React from "react"
import {isEmpty} from "lodash"
import {Validator, withoutDuplications} from "../graph/node-modal/editors/Validators"

type Props = {
  validators: Array<Validator>,
  values: Array<string>,
  additionalClassName?: string,
  validationLabelInfo?: string,
  processNameValidationError?: string
}

export default function ValidationLabels(props: Props) {

  type ValidationErrors = {
    message: string,
    description: string,
  }

  const {validators, values, additionalClassName, validationLabelInfo, processNameValidationError} = props

  const validationErrors: ValidationErrors[] = withoutDuplications(validators)
    .filter(v => !v.isValid(...values))
    .map(validator => ({
      message: validator.message && validator.message(),
      description: validator.description && validator.description(),
    }))

  const isValid: boolean = isEmpty(validationErrors)

  const renderErrorLabels = () => validationErrors.map(
    (validationError, ix) => (
      <span key={ix} className="validation-label-error" title={validationError.description}>
        {validationError.message}
      </span>
    )
  )

  // TODO: We're assuming that we have disjoint union of type info & validation errors, which is not always the case.
  // It's possible that expression is valid and it's type is known, but a different type is expected.
  return (
    <div className={`validation-labels ${additionalClassName}`}>
      <span className="validation-label-error" title={processNameValidationError}>
        {processNameValidationError}
      </span>
      { isValid ? (
        <span className="validation-label-info" title="Info">
          {validationLabelInfo}
        </span>
      ) :
        renderErrorLabels()
      }
    </div>
  )
}
