import PropTypes from "prop-types";
import Input from "./Input";
import Textarea from "./Textarea";
import ExpressionInput from "./ExpressionInput";
import React from "react";
import _ from "lodash";
import {errorValidator, notEmptyValidator} from "../../../common/Validators";
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants";

const Variable = (props) => {

  const {node, onChange, isMarked, readOnly, showValidation, errors} = props;

  return (
    <div className="node-table-body node-variable-builder-body">
      <Input
        label="Id"
        value={node.id}
        path="id"
        onChange={onChange}
        isMarked={isMarked("id")} readOnly={readOnly}
        showValidation={showValidation}
        validators={[notEmptyValidator, errorValidator(errors, "id")]}
      />
      <Input
        label="Variable Name"
        value={node.varName}
        path="varName"
        onChange={onChange}
        isMarked={isMarked("varName")}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[notEmptyValidator, errorValidator(errors, "varName")]}
      />
      <ExpressionInput
        name="expression"
        label="Expression"
        value={node.value}
        path="value.expression"
        onChange={onChange}
        readOnly={readOnly}
        showValidation={showValidation}
        validators={[notEmptyValidator, errorValidator(errors, DEFAULT_EXPRESSION_ID)]}
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
  onChange: PropTypes.func.isRequired,
  showValidation: PropTypes.bool.isRequired
};

Variable.defaultProps = {
  readOnly: false
};

Variable.availableFields = ["id", "varName", DEFAULT_EXPRESSION_ID]

export default Variable;
