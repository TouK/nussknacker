import {css} from "@emotion/css"
import React, {SetStateAction} from "react"
import {useSelector} from "react-redux"
import {Edge, NodeType} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import {ContentSize} from "./ContentSize"
import {SubprocessContent} from "./SubprocessContent"
import {getNodeErrors, getPropertiesErrors} from "./selectors"
import {RootState} from "../../../../reducers"
import {NodeDetailsContent} from "../NodeDetailsContent"

interface Props {
  node: NodeType,
  edges: Edge[],
  onChange?: (node: SetStateAction<NodeType>, edges: SetStateAction<Edge[]>) => void,
}

export function NodeGroupContent({node, edges, onChange}: Props): JSX.Element {
  const errors = node.type ? // for properties node `type` is undefined
      useSelector((state: RootState) => getNodeErrors(state, node.id)) : useSelector(getPropertiesErrors)

  return (
    <div className={css({height: "100%", display: "grid", gridTemplateRows: "auto 1fr"})}>
      <ContentSize>
        <NodeDetailsContent
          node={node}
          edges={edges}
          onChange={onChange}
          nodeErrors={errors}
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

