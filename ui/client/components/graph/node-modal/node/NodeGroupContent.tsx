import {css} from "@emotion/css"
import React, {PropsWithChildren, useMemo} from "react"
import {useSelector} from "react-redux"
import TestResultUtils, {NodeTestResults} from "../../../../common/TestResultUtils"
import {getTestResults} from "../../../../reducers/selectors/graph"
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

export function useNodeTestResults(id: NodeId): NodeTestResults {
  const results = useSelector(getTestResults)
  return useMemo(() => TestResultUtils.resultsForNode(results, id), [id, results])
}

export function NodeGroupContent({children, ...props}: PropsWithChildren<Props>): JSX.Element {
  const {editedNode, readOnly, currentNodeId, updateNodeState, updateEdgesState} = props
  const nodeErrors = useSelector((state: RootState) => getErrors(state, currentNodeId))
  const testResults = useNodeTestResults(currentNodeId)

  return (
    <div className={css({height: "100%", display: "grid", gridTemplateRows: "auto 1fr"})}>
      <ContentSize>
        <NodeDetailsContent
          node={editedNode}
          onChange={(node, edges) => {
            updateEdgesState(edges)
            updateNodeState(node)
          }}
          isEditMode={!readOnly}
          testResults={testResults}
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

