import {NodeId, NodeType, NodeValidationError, ProcessDefinitionData, UIParameter} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"
import React, {useCallback} from "react"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {IdField} from "./IdField"
import {DisableField} from "./DisableField"
import ParameterList from "./ParameterList"
import {ParameterExpressionField} from "./ParameterExpressionField"
import {InputWithFocus} from "../../withFocus"
import {DescriptionField} from "./DescriptionField"

export function SubprocessInput({
  fieldErrors,
  findAvailableVariables,
  isEditMode,
  node,
  originalNodeId,
  parameterDefinitions,
  processDefinitionData,
  renderFieldLabel,
  setProperty,
  showSwitch,
  showValidation,
}: {
  fieldErrors?: NodeValidationError[],
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  isEditMode?: boolean,
  node: NodeType,
  originalNodeId?: NodeId,
  parameterDefinitions: UIParameter[],
  processDefinitionData?: ProcessDefinitionData,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showSwitch?: boolean,
  showValidation?: boolean,
}): JSX.Element {
  const setNodeState = useCallback(newParams => setProperty("ref.parameters", newParams), [setProperty])
  return (
    <NodeTableBody>
      <IdField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <DisableField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      <ParameterList
        processDefinitionData={processDefinitionData}
        editedNode={node}
        savedNode={node}
        setNodeState={setNodeState}
        createListField={(param, index) => {
          return (
            <ParameterExpressionField
              originalNodeId={originalNodeId}
              showSwitch={showSwitch}
              findAvailableVariables={findAvailableVariables}
              parameterDefinitions={parameterDefinitions}
              fieldErrors={fieldErrors}

              node={node}
              isEditMode={isEditMode}
              showValidation={showValidation}
              renderFieldLabel={renderFieldLabel}
              setProperty={setProperty}
              parameter={param}
              listFieldPath={`ref.parameters[${index}]`}
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
      <DescriptionField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}
