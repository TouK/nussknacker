/* eslint-disable i18next/no-literal-string */
import React, {useMemo, useState} from "react"
import {Edge, NodeType,} from "../../../types"
import NodeAdditionalInfoBox from "./NodeAdditionalInfoBox"
import {getParameterDefinitions, useStateCallback} from "./NodeDetailsContentUtils"
import {generateUUIDs, NodeDetailsContent2} from "./NodeDetailsContent2"
import {adjustParameters} from "./ParametersUtils"
import {WithTempId} from "./EdgesDndComponent"
import {NodeDetailsContentProps} from "./NodeDetailsContentProps3"

export const NodeDetailsContent = (props: NodeDetailsContentProps): JSX.Element => {
  const {node, processId, dynamicParameterDefinitions, processDefinitionData, ...passProps} = props

  const parameterDefinitions = useMemo(() => {
    return getParameterDefinitions(processDefinitionData, node, dynamicParameterDefinitions)
  }, [dynamicParameterDefinitions, node, processDefinitionData])

  const [originalNode] = useState(node)

  const [editedNode, setEditedNode] = useStateCallback<NodeType>(
    generateUUIDs(adjustParameters(node, parameterDefinitions).adjustedNode, ["fields", "parameters"])
  )

  const [editedEdges, setEditedEdges] = useStateCallback<WithTempId<Edge>[]>(
    props.edges
  )

  return (
    <>
      <NodeDetailsContent2
        {...passProps}
        node={node}
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

