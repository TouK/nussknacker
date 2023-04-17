import {Edge, EdgeKind, NodeType, NodeValidationError, UIParameter} from "../../../types"
import ProcessUtils from "../../../common/ProcessUtils"
import {useDiffMark} from "./PathsToMark"
import {NodeTableBody} from "./NodeDetailsContent/NodeTable"
import {IdField} from "./IdField"
import {StaticExpressionField} from "./StaticExpressionField"
import {DisableField} from "./DisableField"
import {EdgesDndComponent} from "./EdgesDndComponent"
import {DescriptionField} from "./DescriptionField"
import React from "react"

export function Filter({
  edges,
  fieldErrors,
  findAvailableVariables,
  isEditMode,
  node,
  parameterDefinitions,
  renderFieldLabel,
  setEditedEdges,
  setProperty,
  showSwitch,
  showValidation,
}: {
  edges: Edge[],
  fieldErrors?: NodeValidationError[],
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  isEditMode?: boolean,
  node: NodeType,
  parameterDefinitions: UIParameter[],
  renderFieldLabel: (paramName: string) => JSX.Element,
  setEditedEdges: (edges: Edge[]) => void,
  setProperty: <K extends keyof NodeType>(property: K, newValue: NodeType[K], defaultValue?: NodeType[K]) => void,
  showSwitch?: boolean,
  showValidation?: boolean,
}): JSX.Element {
  const [, isCompareView] = useDiffMark()
  return (
    <NodeTableBody>
      <IdField
        isEditMode={isEditMode}
        showValidation={showValidation}
        node={node}
        setProperty={setProperty}
        renderFieldLabel={renderFieldLabel}

      />
      <StaticExpressionField
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
        fieldLabel={"Expression"}
        parameterDefinitions={parameterDefinitions}
        showSwitch={showSwitch}
        findAvailableVariables={findAvailableVariables}
        showValidation={showValidation}
        fieldErrors={fieldErrors}
        isEditMode={isEditMode}
        node={node}

      />
      <DisableField
        node={node}
        isEditMode={isEditMode}
        showValidation={showValidation}
        renderFieldLabel={renderFieldLabel}
        setProperty={setProperty}
      />
      {!isCompareView ?
        (
          <EdgesDndComponent
            label={"Outputs"}
            nodeId={node.id}
            value={edges}
            onChange={setEditedEdges}
            edgeTypes={[
              {value: EdgeKind.filterTrue, onlyOne: true},
              {value: EdgeKind.filterFalse, onlyOne: true},
            ]}
            readOnly={!isEditMode}
            fieldErrors={fieldErrors || []}
          />
        ) :
        null}
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
