import React from "react"
import {v4 as uuid4} from "uuid"
import {Validator, withoutDuplications} from "../graph/node-modal/editors/Validators"

type Props = {
  validators: Array<Validator>,
  values: Array<string>,
}

export default function ValidationLabels(props: Props) {

  const {validators, values} = props

  console.log(withoutDuplications(validators))

  return (
    <div className={"validation-labels"}>
      {withoutDuplications(validators).map(validator => validator.isValid(...values) ?
        null :
        <span key={uuid4()} className="validation-label" title={validator.description}>{validator.message}</span>)}
    </div>
  )
}
