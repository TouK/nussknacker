import {css} from "@emotion/css"
import React, {SetStateAction} from "react"
import {useSelector} from "react-redux"
import {Edge, NodeType} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import {ContentSize} from "./ContentSize"
import {SubprocessContent} from "./SubprocessContent"
import {getErrors} from "./selectors"
import {RootState} from "../../../../reducers"
import {NodeDetailsContent} from "../NodeDetailsContent"

interface Props {
  node: NodeType,
  edges: Edge[],
  onChange?: (node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => void,
}

export function NodeGroupContent({node, edges, onChange}: Props): JSX.Element {
  const nodeErrors = useSelector((state: RootState) => getErrors(state, node.id))

  return (
    <div className={css({height: "100%", display: "grid", gridTemplateRows: "auto 1fr"})}>
      <ContentSize>
        <NodeDetailsContent
          node={node}
          edges={edges}
          onChange={onChange}
          nodeErrors={nodeErrors}
          showValidation
          showSwitch
        />
      </ContentSize>
      {NodeUtils.nodeIsSubprocess(node) && (
        <SubprocessContent nodeToDisplay={node}/>
      )}
    </div>
  )
}

