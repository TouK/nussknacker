import {css} from "emotion"
import React, {PropsWithChildren} from "react"
import {useSelector} from "react-redux"
import TestResultUtils from "../../../../common/TestResultUtils"
import {getTestResults} from "../../../../reducers/selectors/graph"
import {GroupNodeType, NodeId, NodeType} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import NodeDetailsContent from "../NodeDetailsContent"
import NodeGroupDetailsContent from "../NodeGroupDetailsContent"
import {ContentSize} from "./ContentSize"
import {getErrors} from "./selectors"
import {SubprocessContent} from "./SubprocessContent"

interface Props {
  editedNode: NodeType | GroupNodeType,
  currentNodeId: NodeId,
  readOnly?: boolean,
  updateNodeState: (node: NodeType | GroupNodeType) => void,
}

export function NodeGroupContent({children, ...props}: PropsWithChildren<Props>): JSX.Element {
  const {editedNode, readOnly, currentNodeId, updateNodeState} = props
  const nodeErrors = useSelector(getErrors)
  const testResults = useSelector(getTestResults)
  const nodeTestResults = (id: NodeId) => TestResultUtils.resultsForNode(testResults, id)

  if (NodeUtils.nodeIsGroup(editedNode)) {
    return (
      <ContentSize>
        <NodeGroupDetailsContent
          node={editedNode}
          onChange={updateNodeState}
          readOnly={readOnly}
          testResults={nodeTestResults}
        />
      </ContentSize>
    )
  }

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

