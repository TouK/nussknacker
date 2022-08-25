import {NodeId, NodeType, NodeValidationError, UIParameter} from "../../../types"
import {SourceSinkCommon} from "./SourceSinkCommon"
import {DisableField} from "./DisableField"
import React from "react"
import ProcessUtils from "../../../common/ProcessUtils"

interface SinkProps {
  fieldErrors?: NodeValidationError[],
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  isEditMode?: boolean,
  node: NodeType,
  originalNodeId?: NodeId,
  parameterDefinitions: UIParameter[],
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showSwitch?: boolean,
  showValidation?: boolean,
}

export function Sink({
  fieldErrors,
  findAvailableVariables,
  isEditMode,
  node,
  originalNodeId,
  parameterDefinitions,
  renderFieldLabel,
  setProperty,
  showSwitch,
  showValidation,
}: SinkProps): JSX.Element {
  return (
    <SourceSinkCommon
      originalNodeId={originalNodeId}
      isEditMode={isEditMode}
      showValidation={showValidation}
      showSwitch={showSwitch}
      node={node}
      findAvailableVariables={findAvailableVariables}
      parameterDefinitions={parameterDefinitions}
      fieldErrors={fieldErrors}
      renderFieldLabel={renderFieldLabel}
      setProperty={setProperty}
    >
      <div>
        <DisableField
          isEditMode={isEditMode}
          showValidation={showValidation}
          node={node}
          renderFieldLabel={renderFieldLabel}
          setProperty={setProperty}
        />
      </div>
    </SourceSinkCommon>
  )
}
