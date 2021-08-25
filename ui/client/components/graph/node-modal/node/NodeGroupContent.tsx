import React, {PropsWithChildren} from "react"
import {useSelector} from "react-redux"
import TestResultUtils from "../../../../common/TestResultUtils"
import {getTestResults} from "../../../../reducers/selectors/graph"
import {GroupNodeType, NodeId, NodeType} from "../../../../types"
import NodeUtils from "../../NodeUtils"
import NodeDetailsContent from "../NodeDetailsContent"
import NodeGroupDetailsContent from "../NodeGroupDetailsContent"
import {getErrors} from "./selectors"

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
  return (
    <>
      {NodeUtils.nodeIsGroup(editedNode) ?
        (
          <NodeGroupDetailsContent
            node={editedNode}
            onChange={updateNodeState}
            readOnly={readOnly}
            testResults={nodeTestResults}
          />
        ) :
        (
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
        )}
      {children}
    </>
  )
}

