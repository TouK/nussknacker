/* eslint-disable i18next/no-literal-string */
import React, {PropsWithChildren} from "react"
import {ParameterExpressionField} from "./ParameterExpressionField"
import {IdField} from "./IdField"
import {DescriptionField} from "./DescriptionField"
import {NodeContentMethods, NodeDetailsContentProps3} from "./NodeDetailsContentProps3"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"

export const SourceSinkCommon = ({
  children,
  showSwitch,
  fieldErrors,
  renderFieldLabel,
  showValidation,
  parameterDefinitions,
  isEditMode,
  findAvailableVariables,
  setProperty,
  node,
  originalNodeId,
}: PropsWithChildren<NodeContentMethods
  & Pick<NodeDetailsContentProps3,
  | "showSwitch"
  | "fieldErrors"
  | "parameterDefinitions"
  | "findAvailableVariables"
  | "originalNodeId">>): JSX.Element => {
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
