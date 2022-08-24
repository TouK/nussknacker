import {css} from "@emotion/css"
import React from "react"
import {useSelector} from "react-redux"
import {Edge, NodeId, NodeType} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import {ContentSize} from "./ContentSize"
import {SubprocessContent} from "./SubprocessContent"
import {getErrors} from "./selectors"
import {RootState} from "../../../../reducers"
import {NodeDetailsContent} from "../NodeDetailsContent"

interface Props {
  currentNodeId: NodeId,
  node: NodeType,
  edges: Edge[],
  onChange?: (node: NodeType, edges: Edge[]) => void,
}

export function NodeGroupContent({currentNodeId, node, edges, onChange}: Props): JSX.Element {
  const nodeErrors = useSelector((state: RootState) => getErrors(state, currentNodeId))

  return (
    <div className={css({height: "100%", display: "grid", gridTemplateRows: "auto 1fr"})}>
      <ContentSize>
        <NodeDetailsContent
          originalNodeId={currentNodeId}
          node={node}
          edges={edges}
          onChange={onChange}
          nodeErrors={nodeErrors}
          showValidation
          showSwitch
        />
      </ContentSize>
      {NodeUtils.nodeIsSubprocess(node) && (
        <SubprocessContent nodeToDisplay={node} currentNodeId={currentNodeId}/>
      )}
    </div>
  )
}

