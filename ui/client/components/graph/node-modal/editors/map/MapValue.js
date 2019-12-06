import ExpressionSuggest from "../../../ExpressionSuggest";
import React from "react";
import PropTypes from "prop-types";

export default function MapValue(props) {
  const {rowKey, value, isMarked, paths, validators, showValidation, readOnly, onChange} = props

  return (
    <div className={"node-value field"}>
      <ExpressionSuggest
        fieldName={`value-${rowKey.uuid}`}
        inputProps={{
          onValueChange: (value) => onChange(`${paths}.expression.expression`, value),
          value: value.expression,
          language: value.language,
          readOnly
        }}
        validators={validators}
        isMarked={isMarked}
        showValidation={showValidation}
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
  paths: PropTypes.string
}

