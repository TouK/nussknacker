import PropTypes from "prop-types"
import React from "react"
import EditableEditor from "../EditableEditor"

export default function MapValue(props) {
  const {rowKey, value, isMarked, paths, showValidation, readOnly, onChange, showSwitch, errors, variableTypes} = props

  return (
    <div className={"node-value field"}>
      <EditableEditor
        fieldName={`value-${rowKey.uuid}`}
        errors={errors}
        isMarked={isMarked}
        readOnly={readOnly}
        showValidation={showValidation}
        showSwitch={showSwitch}
        onValueChange={(value) => onChange(`${paths}.expression.expression`, value)}
        expressionObj={value}
        rowClassName={" "}
        valueClassName={" "}
        variableTypes={variableTypes}
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
  showSwitch: PropTypes.bool,
  variableTypes: PropTypes.object.isRequired,
}

