/* eslint-disable i18next/no-literal-string */
import React, {useMemo, useState} from "react"
import ProcessUtils from "../../../common/ProcessUtils"
import {AdditionalPropertyConfig} from "./AdditionalProperty"
import {
  Edge,
  NodeType,
  NodeValidationError,
  ProcessDefinitionData,
  ProcessId,
  UIParameter,
  VariableTypes,
} from "../../../types"
import {UserSettings} from "../../../reducers/userSettings"
import {ValidationRequest} from "../../../actions/nk"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {getParameterDefinitions, useStateCallback} from "./NodeDetailsContentUtils"
import {NodeDetailsContent2} from "./NodeDetailsContent2"

export interface NodeDetailsContentProps {
  isEditMode?: boolean,
  dynamicParameterDefinitions?: UIParameter[],
  currentErrors?: NodeValidationError[],
  processId?: ProcessId,
  additionalPropertiesConfig?: Record<string, AdditionalPropertyConfig>,
  showValidation?: boolean,
  showSwitch?: boolean,
  findAvailableVariables?: ReturnType<typeof ProcessUtils.findAvailableVariables>,
  processDefinitionData?: ProcessDefinitionData,
  node: NodeType,
  edges?: Edge[],
  expressionType?,
  originalNodeId?: NodeType["id"],
  nodeTypingInfo?,
  updateNodeData?: (processId: string, validationRequestData: ValidationRequest) => void,
  findAvailableBranchVariables?,
  processProperties?,
  pathsToMark?: string[],
  onChange?: (node: NodeType, outputEdges?: Edge[]) => void,
  variableTypes?: VariableTypes,
  userSettings: UserSettings,
}

export const NodeDetailsContent = (props: NodeDetailsContentProps): JSX.Element => {
  const {node, processId, dynamicParameterDefinitions, processDefinitionData} = props

  const parameterDefinitions = useMemo(() => {
    return getParameterDefinitions(processDefinitionData, node, dynamicParameterDefinitions)
  }, [dynamicParameterDefinitions, node, processDefinitionData])

  const [originalNode] = useState(node)
  const [editedNode, setEditedNode] = useStateCallback<NodeType>(node)

  return (
    <>
      <NodeDetailsContent2
        {...props}
        {...{
          parameterDefinitions,
          originalNode,
          editedNode,
          setEditedNode,
        }}
      />
      <NodeAdditionalInfoBox node={editedNode} processId={processId}/>
    </>
  )
}

