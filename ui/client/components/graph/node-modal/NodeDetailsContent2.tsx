/* eslint-disable i18next/no-literal-string */
import {Edge, NodeType} from "../../../types"
import React, {useCallback, useEffect, useMemo} from "react"
import {cloneDeep, get, has, partition} from "lodash"
import {v4 as uuid4} from "uuid"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {NodeDetailsContent3} from "./NodeDetailsContent3"
import {EditableEdges, NodeDetailsContentProps2} from "./NodeDetailsContentProps3"

export function generateUUIDs(editedNode: NodeType, properties: string[]): NodeType {
  const node = cloneDeep(editedNode)
  properties.forEach((property) => {
    if (has(node, property)) {
      get(node, property, []).forEach((el) => el.uuid = el.uuid || uuid4())
    }
  })
  return node
}

export function NodeDetailsContent2(props: NodeDetailsContentProps2 & EditableEdges): JSX.Element {
  const {
    currentErrors = [],
    node,
    editedNode,
    editedEdges,
    onChange,
    setEditedNode,
    setEditedEdges,
    parameterDefinitions,
    ...passProps
  } = props

  useEffect(() => {
    onChange?.(editedNode, editedEdges)
  }, [editedEdges, editedNode, onChange])

  const updateNodeState = useCallback((updateNode: (current: NodeType) => NodeType): void => {
    setEditedNode(
      updateNode,
    )
  }, [setEditedNode])

  const setEdgesState = useCallback((nextEdges: Edge[]) => {
    setEditedEdges(
      currentEdges => nextEdges !== currentEdges ? nextEdges : currentEdges,
    )
  }, [setEditedEdges])

  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  return (
    <>
      <NodeErrors errors={otherErrors} message="Node has errors"/>
      <TestResultsWrapper nodeId={node.id}>
        <NodeDetailsContent3
          {...passProps}
          parameterDefinitions={parameterDefinitions}
          node={node}
          setEditedNode={setEditedNode}
          editedNode={editedNode}
          edges={editedEdges}
          setEdgesState={setEdgesState}
          updateNodeState={updateNodeState}
          fieldErrors={fieldErrors}
        />
      </TestResultsWrapper>
    </>
  )
}

