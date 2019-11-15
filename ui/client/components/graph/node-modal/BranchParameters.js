import PropTypes from "prop-types"
import React from "react"
import ExpressionSuggest from "../ExpressionSuggest"
import {errorValidator, notEmptyValidator} from "../../../common/Validators";

const BranchParameters = (props) => {

  const {node, joinDef, onChange, isMarked, readOnly, showValidation, errors} = props

  return (
    joinDef.branchParameters.map((branchParamDef, paramIndex) => {
      return (
        <div className="node-row" key={branchParamDef.name}>
          <div className="node-label" title={branchParamDef.name}>{branchParamDef.name}:</div>
          <div className="node-value">
            <div className="fieldsControl">
              {
                joinDef.incomingEdges.map((edge, edgeIndex) => {
                  // It could be tricky - we assume that node data is filled by template (or actual values)
                  // in the same order as here, but it is true because for filling is used the same JoinDef
                  const path = `branchParameters[${edgeIndex}].parameters[${paramIndex}]`
                  const paramValue = node.branchParameters[edgeIndex].parameters[paramIndex]
                  const fieldName = `value-${branchParamDef.name}-${edge.from}`;
                  return (
                    <div className="branch-parameter-row movable-row" key={`${branchParamDef.name}-${edge.from}`}>
                      <div className={"read-only"}>{edge.from}</div>
                      <div className={"node-value field"}>
                        <ExpressionSuggest
                          fieldName={fieldName}
                          inputProps={{
                            onValueChange: ((value) => onChange(`${path}.expression.expression`, value)),
                            value: paramValue.expression.expression,
                            language: paramValue.expression.language,
                            readOnly
                          }}
                          validators={[notEmptyValidator, errorValidator(errors, branchErrorFieldName(branchParamDef.name, edge.from))]}
                          isMarked={isMarked(path)}
                          showValidation={showValidation}
                        />
                      </div>
                    </div>
                  )
                  }
                )
              }
            </div>
          </div>
        </div>
      );
    }))
}

BranchParameters.propTypes = {
  node: PropTypes.object.isRequired,
  joinDef: PropTypes.object.isRequired,
  onChange: PropTypes.func.isRequired,
  isMarked: PropTypes.func.isRequired,
  readOnly: PropTypes.bool,
  showValidation: PropTypes.bool.required
}

BranchParameters.defaultProps = {
  readOnly: false
}

export default BranchParameters

export const branchErrorFieldName = (paramName, branch) => {
  return `${paramName}` + " for branch " + `${branch}`;
}