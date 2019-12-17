import React from "react"
import PropTypes from "prop-types"
import EditableExpression from "../expression/EditableExpression"

export default function MapValue(props) {
  const {rowKey, value, isMarked, paths, validators, showValidation, readOnly, onChange, showSwitch} = props

  return (
    <div className={"node-value field"}>
      <EditableExpression
        fieldName={`value-${rowKey.uuid}`}
        validators={validators}
        isMarked={isMarked}
        readOnly={readOnly}
        showValidation={showValidation}
        showSwitch={showSwitch}
        onValueChange={(value) => onChange(`${paths}.expression.expression`, value)}
        expressionObj={value}
        rowClassName={null}
        valueClassName={null}
      />
    </div>
  )
}

MapValue.propTypes = {
  value: PropTypes.object.isRequired,
  showValidation: PropTypes.bool,
  validators: PropTypes.array,
  readOnly: PropTypes.bool,
  isMarked: PropTypes.bool,
  onChange: PropTypes.func,
  paths: PropTypes.string,
  showSwitch: PropTypes.bool
}

