import TimeRangeComponent from "./TimeRangeComponent";
import ValidationLabels from "../../../../../modals/ValidationLabels";
import React from "react";
import {Duration, DurationComponentType} from "./DurationEditor";
import {allValid, Validator} from "../../Validators";
import {Period} from "./PeriodEditor";
import './timeRange.styl'

type Props = {
  components: Array<DurationComponentType>,
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
          components.map(component =>
            <TimeRangeComponent
              key={component.fieldName}
              label={component.label}
              onChange={(value) => onComponentValueChange(component.fieldName, value)}
              value={value[component.fieldName]}
              readOnly={readOnly}
              isMarked={isMarked}
              isValid={allValid(validators, [expression])}
              showValidation={showValidation}
            />
          )
        }
      </div>
      {showValidation && <ValidationLabels validators={validators} values={[expression]}/>}
    </div>
  )
}