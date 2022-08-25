import {NodeId, NodeType, NodeValidationError, VariableTypes} from "../../../types"
import {useSelector} from "react-redux"
import {RootState} from "../../../reducers"
import {getNodeExpressionType} from "./NodeDetailsContent/selectors"
import MapVariable from "./MapVariable"
import React from "react"

export function VariableBuilder({
  addElement,
  fieldErrors,
  isEditMode,
  node,
  originalNodeId,
  removeElement,
  renderFieldLabel,
  setProperty,
  showValidation,
  variableTypes,
}: {
  addElement: (...args: any[]) => any,
  fieldErrors?: NodeValidationError[],
  isEditMode?: boolean,
  node: NodeType,
  originalNodeId?: NodeId,
  removeElement: (property: keyof NodeType, index: number) => void,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showValidation?: boolean,
  variableTypes?: VariableTypes,
}): JSX.Element {
  const nodeExpressionType = useSelector((state: RootState) => getNodeExpressionType(state)(originalNodeId))

  return (
    <MapVariable
      renderFieldLabel={renderFieldLabel}
      removeElement={removeElement}
      onChange={setProperty}
      node={node}
      addElement={addElement}
      readOnly={!isEditMode}
      showValidation={showValidation}
      variableTypes={variableTypes}
      errors={fieldErrors || []}
      expressionType={nodeExpressionType}
    />
  )
}
