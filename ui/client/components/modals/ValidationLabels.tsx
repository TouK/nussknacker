import React from "react"
import {v4 as uuid4} from "uuid"
import {Validator, withoutDuplications} from "../graph/node-modal/editors/Validators"

type Props = {
  validators: Array<Validator>,
  values: Array<string>,
  additionalClassName?: string,
}

export default function ValidationLabels(props: Props) {

  const {validators, values, additionalClassName} = props

  return (
    <div className={`validation-labels ${additionalClassName}`}>
      {withoutDuplications(validators).map(validator => validator.isValid(...values) ?
        null :
        <span key={uuid4()} className="validation-label" title={validator.description && validator.description()}>
          {validator.message()}
        </span>)}
    </div>
  )
}
