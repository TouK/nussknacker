import React, {useCallback} from "react"
import {Field, NodeType} from "../../../types"
import {ExpressionLang} from "./editors/expression/types"
import Map from "./editors/map/Map"
import {MapVariableProps} from "./MapVariable"
import {NodeCommonDetailsDefinition} from "./NodeCommonDetailsDefinition"

type Props<F extends Field> = MapVariableProps<F>

function SubprocessOutputDefinition<F extends Field>(props: Props<F>): JSX.Element {
  const {removeElement, addElement, variableTypes, expressionType, ...passProps} = props
  const {node, ...mapProps} = passProps

  const addField = useCallback((namespace: string, field) => {
    const newField: Field = {name: "", expression: {expression: "", language: ExpressionLang.SpEL}}
    addElement(namespace, field || newField)
  }, [addElement])

  return (
    <NodeCommonDetailsDefinition {...passProps} outputName="Output name" outputField="outputName">
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

SubprocessOutputDefinition.availableFields = (node: NodeType) => {
  return ["id", "outputName"]
}

export default SubprocessOutputDefinition
