/* eslint-disable i18next/no-literal-string */
import {Edge, NodeType, UIParameter} from "../../../types"
import {WithTempId} from "./EdgesDndComponent"
import {DispatchWithCallback} from "./NodeDetailsContentUtils"
import React, {SetStateAction, useCallback, useEffect, useMemo} from "react"
import {cloneDeep, get, has, partition} from "lodash"
import {v4 as uuid4} from "uuid"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {NodeDetailsContentProps} from "./NodeDetailsContent"
import {NodeDetailsContent3} from "./NodeDetailsContent3"

export function generateUUIDs(editedNode: NodeType, properties: string[]): NodeType {
  const node = cloneDeep(editedNode)
  properties.forEach((property) => {
    if (has(node, property)) {
      get(node, property, []).forEach((el) => el.uuid = el.uuid || uuid4())
    }
  })
  return node
}

export interface NodeDetailsContentProps2 extends NodeDetailsContentProps {
  parameterDefinitions: UIParameter[],
  originalNode: NodeType,

  editedNode: NodeType,
  setEditedNode: DispatchWithCallback<SetStateAction<NodeType>>,

  editedEdges: WithTempId<Edge>[],
  setEditedEdges: DispatchWithCallback<SetStateAction<WithTempId<Edge>[]>>,
}

export function NodeDetailsContent2(props: NodeDetailsContentProps2): JSX.Element {
  const {
    currentErrors = [],
    node,
    editedNode,
    editedEdges,
    updateNodeData,
    processId,
    findAvailableBranchVariables,
    processProperties,
    findAvailableVariables,
    originalNodeId,
    onChange,
    setEditedNode,
    setEditedEdges,
    parameterDefinitions,
    isEditMode,
  } = props

  const updateNode = useCallback((currentNode: NodeType): void => {
    updateNodeData(processId, {
      variableTypes: findAvailableVariables(originalNodeId),
      branchVariableTypes: findAvailableBranchVariables(originalNodeId),
      nodeData: currentNode,
      processProperties: processProperties,
      outgoingEdges: editedEdges.map(e => ({...e, to: e._id || e.to})),
    })
  }, [editedEdges, findAvailableBranchVariables, findAvailableVariables, originalNodeId, processId, processProperties, updateNodeData])

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

  // useEffect(() => {
  //   console.log("adjustedNode")
  //   const {adjustedNode} = adjustParameters(node, parameterDefinitions)
  //   updateNodeState(() => adjustedNode)
  // }, [node, parameterDefinitions, updateNodeState])

  // useEffect(() => {
  //   console.log("editedNode")
  //   if (isEditMode) {
  //     updateNode(editedNode)
  //   }
  // }, [editedNode, isEditMode, updateNode])

  const [fieldErrors, otherErrors] = useMemo(() => partition(currentErrors, error => !!error.fieldName), [currentErrors])

  return (
    <>
      <NodeErrors errors={otherErrors} message="Node has errors"/>
      <TestResultsWrapper nodeId={node.id}>
        <NodeDetailsContent3
          {...props}
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

