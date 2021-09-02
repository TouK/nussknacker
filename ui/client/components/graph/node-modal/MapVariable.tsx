import React, {useCallback} from "react"
import {Field, NodeType, TypedObjectTypingResult, VariableTypes} from "../../../types"
import {ExpressionLang} from "./editors/expression/types"
import Map from "./editors/map/Map"
import {NodeCommonDetailsDefinition, NodeDetailsProps} from "./NodeCommonDetailsDefinition"

export interface MapVariableProps<F extends Field> extends NodeDetailsProps<F> {
  removeElement: (namespace: string, ix: number) => void,
  addElement: (property: string, element: $TodoType) => void,
  variableTypes: VariableTypes,
  expressionType?: TypedObjectTypingResult,
}

function MapVariable<F extends Field>(props: MapVariableProps<F>): JSX.Element {
  const {removeElement, addElement, variableTypes, expressionType, ...passProps} = props
  const {node, ...mapProps} = passProps

  const addField = useCallback((namespace: string, field) => {
    const newField: Field = {name: "", expression: {expression: "", language: ExpressionLang.SpEL}}
    addElement(namespace, field || newField)
  }, [addElement])

  return (
    <NodeCommonDetailsDefinition {...props} outputName="Variable Name" outputField="varName">
      <Map
        {...mapProps}
        label="Fields"
        namespace="fields"
        fields={node.fields}
        removeField={removeElement}
        addField={addField}
        variableTypes={variableTypes}
        expressionType={expressionType}
      />
    </NodeCommonDetailsDefinition>
  )
}

MapVariable.availableFields = (node: NodeType) => {
  return ["id", "varName"]
}

export default MapVariable
