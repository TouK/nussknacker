import _ from "lodash"
import React from "react"
import {ParameterExpressionField} from "./ParameterExpressionField";
import {InputWithFocus} from "../../withFocus";
import ParameterList from "./ParameterList";

const paramsFieldName = "outputParameters"

export default function ParameterOutputList({
  editedNode,
  processDefinitionData,
  parameterDefinitions,
  savedNode,
  setNodeState,
  showSwitch,
  findAvailableVariables,
  fieldErrors,
  isEditMode,
  showValidation,
  renderFieldLabel,
  setProperty,
}) {
  const nodeDefinitionParameters = _.get(editedNode?.ref, paramsFieldName, [])
  return nodeDefinitionParameters && nodeDefinitionParameters.length === 0 ? null : (
    <div className="node-row" key="outputParameters">
      <div className="node-label" title="Fragment outputs names">Outputs names:</div>
      <div className="node-value">
        <div className="fieldsControl">
          <ParameterList
              paramsFieldName={paramsFieldName}
              processDefinitionData={processDefinitionData}
              editedNode={editedNode}
              savedNode={savedNode}
              setNodeState={setNodeState}
              createListField={(param, index) => {
                return (
                    <ParameterExpressionField
                        showSwitch={showSwitch}
                        findAvailableVariables={findAvailableVariables}
                        parameterDefinitions={parameterDefinitions}
                        fieldErrors={fieldErrors}
                        node={editedNode}
                        isEditMode={isEditMode}
                        showValidation={showValidation}
                        renderFieldLabel={renderFieldLabel}
                        setProperty={setProperty}
                        parameter={param}
                        listFieldPath={`ref.${paramsFieldName}[${index}]`}
                    />
                )
              }}
              createReadOnlyField={params => (
                  <div className="node-row">
                    {renderFieldLabel(params.name)}
                    <div className="node-value">
                      <InputWithFocus
                          type="text"
                          className="node-input"
                          value={params.expression.expression}
                          disabled={true}
                      />
                    </div>
                  </div>
              )}
          />
        </div>
      </div>
    </div>
  )
}
