import PropTypes from "prop-types"
import React from "react"
import EditableEditor from "./editors/EditableEditor"

const BranchParameters = (props) => {

  const {node, joinDef, onChange, isMarked, readOnly, showValidation, errors, showSwitch} = props

  return (
    joinDef.branchParameters.map((param, paramIndex) => {
      return (
        <div className="node-row" key={param.name}>
          <div className="node-label" title={param.name}>{param.name}:</div>
          <div className="node-value">
            <div className="fieldsControl">
              {
                joinDef.incomingEdges.map((edge, edgeIndex) => {
                  // It could be tricky - we assume that node data is filled by template (or actual values)
                  // in the same order as here, but it is true because for filling is used the same JoinDef
                  const path = `branchParameters[${edgeIndex}].parameters[${paramIndex}]`
                  const paramValue = node.branchParameters[edgeIndex].parameters[paramIndex]
                  const fieldName = `value-${param.name}-${edge.from}`
                  return (
                    <div className="branch-parameter-row" key={`${param.name}-${edge.from}`}>
                      <div className={"branch-param-label"}>{edge.from}</div>
                      <div className={"branch-parameter-expr-container"}>
                        <EditableEditor
                          fieldType={"expression"}
                          fieldName={branchErrorFieldName(param.name, edge.from)}
                          fieldLabel={null}
                          onValueChange={((value) => onChange(`${path}.expression.expression`, value))}
                          expressionObj={paramValue.expression}
                          readOnly={readOnly}
                          isMarked={isMarked(path)}
                          showValidation={showValidation}
                          rowClassName={"branch-parameter-expr"}
                          valueClassName={"branch-parameter-expr-value"}
                          showSwitch={showSwitch}
                          errors={errors}
                        />
                      </div>
                    </div>
                  )
                })
              }
            </div>
          </div>
        </div>
      )
    }))
}

BranchParameters.propTypes = {
  node: PropTypes.object.isRequired,
  joinDef: PropTypes.object.isRequired,
  onChange: PropTypes.func.isRequired,
  isMarked: PropTypes.func.isRequired,
  readOnly: PropTypes.bool,
  showValidation: PropTypes.bool.isRequired,
  showSwitch: PropTypes.bool,
}

BranchParameters.defaultProps = {
  readOnly: false,
}

export default BranchParameters

export const branchErrorFieldName = (paramName, branch) => {
  return `${paramName} for branch ${branch}`
}
