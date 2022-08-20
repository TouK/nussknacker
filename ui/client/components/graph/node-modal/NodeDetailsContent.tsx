/* eslint-disable i18next/no-literal-string */
import React, {useCallback, useEffect, useMemo, useState} from "react"
import {Edge, NodeType} from "../../../types"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {getParameterDefinitions} from "./NodeDetailsContentUtils"
import {NodeDetailsContent2} from "./NodeDetailsContent2"
import {adjustParameters} from "./ParametersUtils"
import {WithTempId} from "./EdgesDndComponent"
import {NodeDetailsContentProps} from "./NodeDetailsContentProps3"
import {cloneDeep, get, has} from "lodash"
import {v4 as uuid4} from "uuid"

function generateUUIDs(editedNode: NodeType, properties: string[]): NodeType {
  const node = cloneDeep(editedNode)
  properties.forEach((property) => {
    if (has(node, property)) {
      get(node, property, []).forEach((el) => el.uuid = el.uuid || uuid4())
    }
  })
  return node
}

export const NodeDetailsContent = ({
  dynamicParameterDefinitions,
  edges,
  node,
  processDefinitionData,
  processId,
  updateNodeData,
  ...passProps
}: NodeDetailsContentProps): JSX.Element => {
  const [originalNode] = useState(node)
  const parameterDefinitions = useMemo(() => {
    return getParameterDefinitions(processDefinitionData, originalNode, dynamicParameterDefinitions)
  }, [dynamicParameterDefinitions, originalNode, processDefinitionData])

  const adjustNode = useCallback((node: NodeType) => {
    const {adjustedNode} = adjustParameters(node, parameterDefinitions)
    return generateUUIDs(adjustedNode, ["fields", "parameters"])
  }, [parameterDefinitions])

  const [editedNode, setEditedNode] = useState<NodeType>(originalNode)

  useEffect(() => {
    setEditedNode(adjustNode)
  }, [adjustNode])

  const [editedEdges, setEditedEdges] = useState<WithTempId<Edge>[]>(edges)

  useEffect(() => {
    updateNodeData(editedNode, editedEdges)
  }, [editedEdges, editedNode, updateNodeData])

  return (
    <>
      <NodeDetailsContent2
        {...passProps}
        node={node}
        edges={edges}
        parameterDefinitions={parameterDefinitions}
        originalNode={originalNode}
        editedNode={editedNode}
        setEditedNode={setEditedNode}
        editedEdges={editedEdges}
        setEditedEdges={setEditedEdges}
        processDefinitionData={processDefinitionData}
      />
      <NodeAdditionalInfoBox node={node} processId={processId}/>
    </>
  )
}

