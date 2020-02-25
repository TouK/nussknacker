import TimeRangeComponent from "./TimeRangeComponent"
import ValidationLabels from "../../../../../modals/ValidationLabels"
import React from "react"
import {Duration} from "./DurationEditor"
import {allValid, Validator} from "../../Validators"
import {Period} from "./PeriodEditor"
import "./timeRange.styl"

type Props = {
  components: Array<string>,
  onComponentValueChange: Function,
  readOnly: boolean,
  showValidation: boolean,
  validators: Array<Validator>,
  value: Duration | Period,
  expression: string,
  isMarked: boolean,
}

export default function TimeRangeSection(props: Props) {

  const {
    components, onComponentValueChange, readOnly, showValidation, validators, value, expression, isMarked,
  } = props

  return (
    <div className={"time-range-section"}>
      <div className={"time-range-components"}>
        {
          components.map(component => (
            <TimeRangeComponent
              key={component}
              label={component}
              onChange={(value) => onComponentValueChange(component, value)}
              value={value[component]}
              readOnly={readOnly}
              isMarked={isMarked}
              isValid={allValid(validators, [expression])}
              showValidation={showValidation}
            />
          ))
        }
      </div>
      {showValidation && <ValidationLabels validators={validators} values={[expression]}/>}
    </div>
  )
}
