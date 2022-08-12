import {css} from "@emotion/css"
import React, {PropsWithChildren, useCallback} from "react"
import {useSelector} from "react-redux"
import {Edge, NodeId, NodeType} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import NodeDetailsContent from "../NodeDetailsContent/NodeDetailsContentConnected"
import {ContentSize} from "./ContentSize"
import {SubprocessContent} from "./SubprocessContent"
import {getErrors} from "./selectors"
import {RootState} from "../../../../reducers"

interface Props {
  editedNode: NodeType,
  outputEdges: Edge[],
  currentNodeId: NodeId,
  readOnly?: boolean,
  updateNodeState: (node: NodeType) => void,
  updateEdgesState: (edges: Edge[]) => void,
}

export function NodeGroupContent({children, ...props}: PropsWithChildren<Props>): JSX.Element {
  const {editedNode, readOnly, currentNodeId, updateNodeState, updateEdgesState} = props
  const nodeErrors = useSelector((state: RootState) => getErrors(state, currentNodeId))

  const onChange = useCallback(
    (node, edges) => {
      updateEdgesState(edges)
      updateNodeState(node)
    },
    [updateEdgesState, updateNodeState]
  )

  return (
    <div className={css({height: "100%", display: "grid", gridTemplateRows: "auto 1fr"})}>
      <ContentSize>
        <NodeDetailsContent
          node={editedNode}
          onChange={onChange}
          isEditMode={!readOnly}
          showValidation={true}
          showSwitch={true}
          originalNodeId={currentNodeId}
          nodeErrors={nodeErrors}
          edges={props.outputEdges}
        />
      </ContentSize>
      {NodeUtils.nodeIsSubprocess(editedNode) && (
        <SubprocessContent nodeToDisplay={editedNode} currentNodeId={currentNodeId}/>
      )}
    </div>
  )
}

