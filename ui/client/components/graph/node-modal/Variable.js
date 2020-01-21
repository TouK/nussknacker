import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import {DEFAULT_EXPRESSION_ID} from "../../../common/graph/constants"
import {errorValidator, notEmptyValidator} from "../../../common/Validators"
import EditableExpression from "./editors/expression/EditableExpression"
import {SimpleEditorTypes} from "./editors/expression/EditorType"
import LabeledInput from "./editors/field/LabeledInput"
import LabeledTextarea from "./editors/field/LabeledTextarea"

const Variable = (props) => {

  const {node, onChange, isMarked, readOnly, showValidation, errors, parameter, renderFieldLabel} = props

  return (
    <div className="node-table-body node-variable-builder-body">
      <LabeledInput renderFieldLabel={() => renderFieldLabel("Name")}
                    value={node.id}
                    onChange={(event) => onChange("id", event.target.value)}
                    isMarked={isMarked("id")} readOnly={readOnly}
                    showValidation={showValidation}
                    validators={[notEmptyValidator, errorValidator(errors, "id")]}/>
      <LabeledInput renderFieldLabel={() => renderFieldLabel("Variable Name")}
                    value={node.varName}
                    onChange={(event) => onChange("varName", event.target.value)}
                    isMarked={isMarked("varName")}
                    readOnly={readOnly}
                    showValidation={showValidation}
                    validators={[notEmptyValidator, errorValidator(errors, "varName")]}/>
      <EditableExpression
        fieldType={SimpleEditorTypes.RAW_EDITOR}
        fieldName="Expression"
        fieldLabel={"Expression"}
        renderFieldLabel={renderFieldLabel}
        expressionObj={node.value}
        onValueChange={((value) => onChange("value.expression", value))}
        readOnly={readOnly}
        showValidation={showValidation}
        showSwitch={false}
        validators={[notEmptyValidator, errorValidator(errors, DEFAULT_EXPRESSION_ID)]}
      />
      <LabeledTextarea renderFieldLabel={() => renderFieldLabel("Description")}
                       value={_.get(node, "additionalFields.description", "")}
                       path="additionalFields.description"
                       onChange={(event) => onChange("additionalFields.description", event.target.value)}
                       isMarked={isMarked("additionalFields.description")}
                       readOnly={readOnly}
                       className={"node-input"}/>
    </div>
  )
}

Variable.propTypes = {
  readOnly: PropTypes.bool,
  isMarked: PropTypes.func.isRequired,
  node: PropTypes.object.isRequired,
  onChange: PropTypes.func.isRequired,
  showValidation: PropTypes.bool.isRequired,
  showSwitch: PropTypes.bool,
}

Variable.defaultProps = {
  readOnly: false,
}

Variable.availableFields = ["id", "varName", DEFAULT_EXPRESSION_ID]

export default Variable
