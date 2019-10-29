import PropTypes from "prop-types";
import Input from "./Input";
import Textarea from "./Textarea";
import ExpressionInput from "./ExpressionInput";
import React from "react";
import _ from "lodash";

const Variable = (props) => {

  const {node, onChange, isMarked, readOnly, handlePropertyValidation} = props;

  return (
    <div className="node-table-body node-variable-builder-body">
      <Input
        label="Id"
        value={node.id}
        path="id"
        onChange={(property, value) => {
          onChange(property, value);
          handlePropertyValidation(property, !_.isEmpty(value))
        }}
        isMarked={isMarked("id")} readOnly={readOnly}
        isValid={(value) => _.isEmpty(value)}
      />
      <Input
        label="Variable Name"
        value={node.varName}
        path="varName"
        onChange={(property, value) => {
          onChange(property, value);
          handlePropertyValidation(property, !_.isEmpty(value))
        }}
        isMarked={isMarked("varName")}
        readOnly={readOnly}
        isValid={(value) => _.isEmpty(value)}
      />
      <ExpressionInput
        name="expression"
        label="Expression"
        value={node.value}
        path="value.expression"
        onChange={(property, value) => {
          onChange(property, value);
          handlePropertyValidation(property, !_.isEmpty(value))
        }}
        isValid={(value) => _.isEmpty(value)}
        readOnly={readOnly}
      />
      <Textarea
        label="Description"
        value={_.get(node, "additionalFields.description", "")}
        path="additionalFields.description"
        onChange={props.onChange}
        isMarked={isMarked("additionalFields.description")}
        readOnly={readOnly}
      />
    </div>
  );
};

Variable.propTypes = {
    readOnly: PropTypes.bool,
    isMarked: PropTypes.func.isRequired,
    node: PropTypes.object.isRequired,
    onChange: PropTypes.func.isRequired
};

Variable.defaultProps = {
    readOnly: false
};

export default Variable;