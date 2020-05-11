import PropTypes from "prop-types"
import React from "react"
import ExpressionField from "./editors/expression/ExpressionField"

const BranchParameters = (props) => {

  const {node, isMarked, showValidation, errors, showSwitch, isEditMode,
    nodeObjectDetails, setNodeDataAt, testResultsToShow, testResultsToHide, toggleTestResult} = props

  //TODO: maybe we can rely only on node?
  const branchParameters = nodeObjectDetails?.parameters.filter(p => p.branchParam)
  return (
    branchParameters?.map((param) => {
      const paramName = param.name
      return (
        <div className="node-row" key={paramName}>
          <div className="node-label" title={paramName}>{paramName}:</div>
          <div className="node-value">
            <div className="fieldsControl">
              {
                node.branchParameters.map((branchParameter, branchIndex) => {
                  const branchId = branchParameter.branchId
                  //here we assume the parameters are correct wrt branch definition. If this is not the case,
                  //differences should be handled on other level, e.g. using reducers etc.
                  const paramIndex = branchParameter.parameters.findIndex(paramInBranch => paramInBranch.name === paramName)
                  const paramValue = branchParameter.parameters[paramIndex]
                  const expressionPath = `branchParameters[${branchIndex}].parameters[${paramIndex}].expression`
                  return paramValue ? (
                    <div className="branch-parameter-row" key={`${paramName}-${branchId}`}>
                      <div className={"branch-param-label"}>{branchId}</div>
                      <div className={"branch-parameter-expr-container"}>
                        <ExpressionField
                          fieldName={null}
                          fieldLabel={paramName}
                          fieldType={null}
                          exprPath={expressionPath}
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
