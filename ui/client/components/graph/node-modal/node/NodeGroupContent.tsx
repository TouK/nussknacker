import {css} from "@emotion/css"
import React, {PropsWithChildren} from "react"
import {useSelector} from "react-redux"
import TestResultUtils from "../../../../common/TestResultUtils"
import {getTestResults} from "../../../../reducers/selectors/graph"
import {NodeId, NodeType} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import NodeDetailsContent from "../NodeDetailsContent"
import {ContentSize} from "./ContentSize"
import {getErrors} from "./selectors"
import {SubprocessContent} from "./SubprocessContent"

interface Props {
  editedNode: NodeType,
  currentNodeId: NodeId,
  readOnly?: boolean,
  updateNodeState: (node: NodeType) => void,
}

export function NodeGroupContent({children, ...props}: PropsWithChildren<Props>): JSX.Element {
  const {editedNode, readOnly, currentNodeId, updateNodeState} = props
  const nodeErrors = useSelector(getErrors)
  const testResults = useSelector(getTestResults)
  const nodeTestResults = (id: NodeId) => TestResultUtils.resultsForNode(testResults, id)

  return (
    <div className={css({height: "100%", display: "grid", gridTemplateRows: "auto 1fr"})}>
      <ContentSize>
        <NodeDetailsContent
          node={editedNode}
          onChange={updateNodeState}
          isEditMode={!readOnly}
          testResults={nodeTestResults(currentNodeId)}
          showValidation={true}
          showSwitch={true}
          originalNodeId={currentNodeId}
          nodeErrors={nodeErrors}
        />
      </ContentSize>
      {NodeUtils.nodeIsSubprocess(editedNode) && (
        <SubprocessContent nodeToDisplay={editedNode} currentNodeId={currentNodeId}/>
      )}
    </div>
  )
}

