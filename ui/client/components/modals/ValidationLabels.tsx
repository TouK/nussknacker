import React from "react"
import _ from "lodash"
import {Validator, withoutDuplications} from "../graph/node-modal/editors/Validators"

type Props = {
  validators: Array<Validator>,
  values: Array<string>,
  additionalClassName?: string,
  validationLabelInfo?: string,
}

export default function ValidationLabels(props: Props) {

  type ValidationErrors = {
    message: string,
    description: string,
  }

  const {validators, values, additionalClassName, validationLabelInfo} = props

  const validationErrors: ValidationErrors[] = withoutDuplications(validators)
    .filter(v => !v.isValid(...values))
    .map(validator => ({
      message: validator.message && validator.message(),
      description: validator.description && validator.description(),
    }))

  const isValid: boolean = _.isEmpty(validationErrors)

  const renderErrorLablels = () => validationErrors.map(
    (validationError, ix) => (
      <span key={ix} className="validation-label-error" title={validationError.description}>
        {validationError.message}
      </span>
    )
  )

  return (
    <div className={`validation-labels ${additionalClassName}`}>
      { isValid ? (
        <span className="validation-label-info" title="Info">
          {validationLabelInfo}
        </span>
      ) :
        renderErrorLablels()
      }
    </div>
  )
}
