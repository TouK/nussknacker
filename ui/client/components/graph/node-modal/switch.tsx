import {
  Edge,
  EdgeKind,
  NodeId,
  NodeType,
  NodeValidationError,
  ProcessDefinitionData,
  UIParameter,
  VariableTypes,
} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"
import {errorValidator} from "./editors/Validators"
import {isEqual} from "lodash"
import {useDiffMark} from "./PathsToMark"
import {useSelector} from "react-redux"
import {RootState} from "../../../reducers"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {IdField} from "./IdField"
import {StaticExpressionField} from "./StaticExpressionField"
import {NodeField} from "./NodeField"
import {FieldType} from "./editors/field/Field"
import {EdgesDndComponent} from "./EdgesDndComponent"
import {DescriptionField} from "./DescriptionField"
import React from "react"
import {getNodeExpressionType} from "./NodeDetailsContent/selectors"

export function Switch({
  edges,
  fieldErrors,
  findAvailableVariables,
  isEditMode,
  node,
  originalNodeId,
  parameterDefinitions,
  processDefinitionData,
  renderFieldLabel,
  setEditedEdges,
  setProperty,
  showSwitch,
  showValidation,
  variableTypes,
}: {
  edges: Edge[],
  fieldErrors?: NodeValidationError[],
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  isEditMode?: boolean,
  node: NodeType,
  originalNodeId?: NodeId,
  parameterDefinitions: UIParameter[],
  processDefinitionData?: ProcessDefinitionData,
  renderFieldLabel: (paramName: string) => JSX.Element,
  setEditedEdges: (edges: Edge[]) => void,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showSwitch?: boolean,
  showValidation?: boolean,
  variableTypes?: VariableTypes,
}): JSX.Element {
  const {node: definition} = processDefinitionData.componentGroups?.flatMap(g => g.components).find(c => c.node.type === node.type)
  const currentExpression = node["expression"]
  const currentExprVal = node["exprVal"]
  const exprValValidator = errorValidator(fieldErrors || [], "exprVal")
  const showExpression = definition["expression"] ? !isEqual(definition["expression"], currentExpression) : currentExpression?.expression
  const showExprVal = !exprValValidator.isValid() || definition["exprVal"] ? definition["exprVal"] !== currentExprVal : currentExprVal
  const [, isCompareView] = useDiffMark()

  const nodeExpressionType = useSelector((state: RootState) => getNodeExpressionType(state)(originalNodeId))

  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {showExpression ?
        (
          <StaticExpressionField
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
            fieldLabel={"Expression (deprecated)"}
          />
        ) :
        null}
      {showExprVal ?
        (
          <NodeField
            isEditMode={isEditMode}
            showValidation={showValidation}
            node={node}

            renderFieldLabel={renderFieldLabel}
            setProperty={setProperty}
            fieldType={FieldType.input}
            fieldLabel={"exprVal (deprecated)"}
            fieldProperty={"exprVal"}
            validators={[errorValidator(fieldErrors || [], "exprVal")]}
          />
        ) :
        null}
      {!isCompareView ?
        (
          <EdgesDndComponent
            label={"Conditions"}
            nodeId={originalNodeId}
            value={edges}
            onChange={setEditedEdges}
            edgeTypes={[
              {value: EdgeKind.switchNext},
              {value: EdgeKind.switchDefault, onlyOne: true, disabled: true},
            ]}
            ordered
            readOnly={!isEditMode}
            variableTypes={node["exprVal"] ?
              {
                ...variableTypes,
                [node["exprVal"]]: nodeExpressionType,
              } :
              variableTypes}
            fieldErrors={fieldErrors || []}
          />
        ) :
        null}
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
