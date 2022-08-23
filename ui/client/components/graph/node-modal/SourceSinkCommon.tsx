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
  isMarked,
  findAvailableVariables,
  setProperty,
  editedNode,
  originalNodeId,
}: PropsWithChildren<NodeContentMethods
  & Pick<NodeDetailsContentProps3,
  | "showSwitch"
  | "fieldErrors"
  | "showValidation"
  | "parameterDefinitions"
  | "isEditMode"
  | "findAvailableVariables"
  | "editedNode"
  | "originalNodeId">>): JSX.Element => {
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        editedNode={editedNode}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {editedNode.ref.parameters?.map((param, index) => (
        <div className="node-block" key={editedNode.id + param.name + index}>
          <ParameterExpressionField
            originalNodeId={originalNodeId}
            isEditMode={isEditMode}
            showValidation={showValidation}
            showSwitch={showSwitch}
            editedNode={editedNode}
            findAvailableVariables={findAvailableVariables}
            parameterDefinitions={parameterDefinitions}
            fieldErrors={fieldErrors}
            isMarked={isMarked}
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
        editedNode={editedNode}
        isMarked={isMarked}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
    </NodeTableBody>
  )
}
