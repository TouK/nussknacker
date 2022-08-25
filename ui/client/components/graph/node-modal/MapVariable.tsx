/* eslint-disable i18next/no-literal-string */
import React, {useCallback} from "react"
import {Field, NodeType, TypedObjectTypingResult, VariableTypes} from "../../../types"
import {ExpressionLang} from "./editors/expression/types"
import Map from "./editors/map/Map"
import {NodeCommonDetailsDefinition} from "./NodeCommonDetailsDefinition"
import {Error} from "./editors/Validators"

export interface MapVariableProps<F extends Field> {
  node: NodeType<F>,
  onChange: (propToMutate: string, newValue: unknown) => void,
  readOnly?: boolean,
  showValidation: boolean,
  renderFieldLabel: (label: string) => React.ReactNode,
  fieldErrors: Error[],
  removeElement: (namespace: string, ix: number) => void,
  addElement: (property: string, element: F) => void,
  variableTypes: VariableTypes,
  expressionType?: Partial<TypedObjectTypingResult>,
}

function MapVariable<F extends Field>(props: MapVariableProps<F>): JSX.Element {
  const {removeElement, addElement, variableTypes, expressionType, ...passProps} = props
  const {node, ...mapProps} = passProps

  const addField = useCallback((namespace, field) => {
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

export default MapVariable
