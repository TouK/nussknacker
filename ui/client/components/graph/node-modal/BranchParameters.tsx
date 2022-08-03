import React from "react"
import ExpressionField from "./editors/expression/ExpressionField"
import ProcessUtils from "../../../common/ProcessUtils"
import {NodeType, UIParameter} from "../../../types"
import {Error} from "./editors/Validators"
import {NodeResultsForContext} from "../../../common/TestResultUtils"

export interface BranchParametersProps {
  node: NodeType,
  isMarked: (path: string) => boolean,
  parameterDefinitions: UIParameter[],
  errors: Error[],
  setNodeDataAt: <T extends any>(propToMutate: string, newValue: T, defaultValue?: T) => void,
  findAvailableVariables: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  testResultsToShow: NodeResultsForContext,
  isEditMode?: boolean,
  showValidation?: boolean,
  showSwitch?: boolean,
}

export default function BranchParameters(props: BranchParametersProps): JSX.Element {
  const {
    node, isMarked, showValidation, errors, showSwitch, isEditMode,
    parameterDefinitions, setNodeDataAt, testResultsToShow, findAvailableVariables,
  } = props

  //TODO: maybe we can rely only on node?
  const branchParameters = parameterDefinitions?.filter(p => p.branchParam)
  return (
    <>
      {branchParameters?.map((param) => {
        const paramName = param.name
        return (
          <div className="node-row" key={paramName}>
            <div className="node-label" title={paramName}>{paramName}:</div>
            <div className="node-value">
              <div className="fieldsControl">
                {node.branchParameters.map((branchParameter, branchIndex) => {
                  const branchId = branchParameter.branchId
                  //here we assume the parameters are correct wrt branch definition. If this is not the case,
                  //differences should be handled on other level, e.g. using reducers etc.
                  const paramIndex = branchParameter.parameters.findIndex(paramInBranch => paramInBranch.name === paramName)
                  const paramValue = branchParameter.parameters[paramIndex]
                  const expressionPath = `branchParameters[${branchIndex}].parameters[${paramIndex}].expression`

                  const contextId = ProcessUtils.findContextForBranch(node, branchId)
                  const variables = findAvailableVariables(contextId, param)

                  if (!paramValue) {
                    return null
                  }

                  return (
                    <div className="branch-parameter-row" key={`${paramName}-${branchId}`}>
                      <div className={"branch-param-label"}>{branchId}</div>
                      <div className={"branch-parameter-expr-container"}>
                        <ExpressionField
                          fieldName={`${paramName} for branch ${branchId}`}
                          fieldLabel={paramName}
                          exprPath={expressionPath}
                          isEditMode={isEditMode}
                          editedNode={node}
                          isMarked={isMarked}
                          showValidation={showValidation}
                          showSwitch={showSwitch}
                          parameterDefinition={param}
                          setNodeDataAt={setNodeDataAt}
                          testResultsToShow={testResultsToShow}
                          renderFieldLabel={() => false}
                          variableTypes={variables}
                          errors={errors}
                        />
                      </div>
                    </div>
                  )
                })}
              </div>
            </div>
          </div>
        )
      })}
    </>
  )
}
