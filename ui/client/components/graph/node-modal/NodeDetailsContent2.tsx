/* eslint-disable i18next/no-literal-string */
import React, {useEffect, useMemo} from "react"
import {partition} from "lodash"
import NodeErrors from "./NodeErrors"
import {TestResultsWrapper} from "./TestResultsWrapper"
import {NodeDetailsContent3} from "./NodeDetailsContent3"
import {EditableEdges, NodeDetailsContentProps2} from "./NodeDetailsContentProps3"

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
          setEdgesState={setEditedEdges}
          updateNodeState={setEditedNode}
          fieldErrors={fieldErrors}
        />
      </TestResultsWrapper>
    </>
  )
}

