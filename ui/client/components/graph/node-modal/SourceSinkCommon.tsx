/* eslint-disable i18next/no-literal-string */
import React, {PropsWithChildren} from "react"
import {ParameterExpressionField} from "./ParameterExpressionField"
import {IdField} from "./IdField"
import {DescriptionField} from "./DescriptionField"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {NodeId, NodeType, NodeValidationError, UIParameter} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"

interface SourceSinkCommonProps {
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

export const SourceSinkCommon = ({
  children,
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
}: PropsWithChildren<SourceSinkCommonProps>): JSX.Element => {
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {node.ref.parameters?.map((param, index) => (
        <div className="node-block" key={node.id + param.name + index}>
          <ParameterExpressionField
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

            parameter={param}
            listFieldPath={`ref.parameters[${index}]`}
          />
        </div>
      ))}
      {children}
      <DescriptionField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}
