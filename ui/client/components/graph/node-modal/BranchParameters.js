import PropTypes from "prop-types"
import React from "react"
import EditableEditor from "./editors/EditableEditor"
import ExpressionField from "./editors/expression/ExpressionField"

const BranchParameters = (props) => {

  const {node, joinDef, isMarked, showValidation, errors, showSwitch, isEditMode,
    nodeObjectDetails, setNodeDataAt, testResultsToShow, testResultsToHide, toggleTestResult} = props

  return (
    joinDef.branchParameters?.map((param, paramIndex) => {
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
                  const paramValue = node?.branchParameters[edgeIndex]?.parameters[paramIndex]
                  return paramValue ? (
                    <div className="branch-parameter-row" key={`${param.name}-${edge.from}`}>
                      <div className={"branch-param-label"}>{edge.from}</div>
                      <div className={"branch-parameter-expr-container"}>
                        <ExpressionField
                          fieldName={null}
                          fieldLabel={param.name}
                          fieldType={null}
                          exprPath={`${path}.expression`}
                          isEditMode={isEditMode}
                          editedNode={node}
                          isMarked={isMarked}
                          showValidation={showValidation}
                          showSwitch={showSwitch}
                          nodeObjectDetails={nodeObjectDetails}
                          setNodeDataAt={setNodeDataAt}
                          testResultsToShow={testResultsToShow}
                          testResultsToHide={testResultsToHide}
                          toggleTestResult={toggleTestResult}
                          renderFieldLabel={() => false}
                          errors={errors}
                        />
                      </div>
                    </div>
                  ) : null
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
  isMarked: PropTypes.func.isRequired,
  isEditMode: PropTypes.bool,
  showValidation: PropTypes.bool.isRequired,
  showSwitch: PropTypes.bool,
  nodeObjectDetails: PropTypes.any,
  setNodeDataAt: PropTypes.func.isRequired,
  testResultsToShow: PropTypes.any,
  testResultsToHide: PropTypes.any,
  toggleTestResult: PropTypes.func.isRequired,
}

BranchParameters.defaultProps = {
  readOnly: false,
}

export default BranchParameters

export const branchErrorFieldName = (paramName, branch) => {
  return `${paramName} for branch ${branch}`
}
