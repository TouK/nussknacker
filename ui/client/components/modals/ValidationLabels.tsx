import React from "react"
import {v4 as uuid4} from "uuid"
import {Validator, withoutDuplications} from "../graph/node-modal/editors/Validators"
import { isEmpty, every, groupBy } from "lodash"

type Props = {
  validators: Array<Validator>,
  values: Array<string>,
  additionalClassName?: string,
  isOptionalParameter?: Boolean
}

export default function ValidationLabels(props: Props) {

  const { validators, values, additionalClassName, isOptionalParameter } = props

  const isValid = (validator: Validator) => {
    if (isEmpty(...values) && !isOptionalParameter && validator.skipOnBlankIfNotRequired) return true
    else return validator.isValid(...values)
  }

  const mapValidators = (validators: Array<Validator>) =>
    validators.map(validator => isValid(validator) ?
      null : (<span key={uuid4()} className="validation-label" title={validator.description && validator.description()}>
        {validator.message()}
      </span>
      ))

  const validateByPriorities = (validators: Array<Validator>) => {
    const groupedValidators = groupBy(withoutDuplications(validators), v => v.priority)
    let result = null
    Object.keys(groupedValidators).sort().forEach(priority => {
      result = mapValidators(groupedValidators[priority])
      if (result !== null) return result
    })
    return null
  }

  return (
    <div className={`validation-labels ${additionalClassName}`}>
      {
        every(validators, v => v.priority) ?
          validateByPriorities(validators) : mapValidators(validators)
      }
    </div>
  )
}
