import React, {useEffect, useState} from "react"
import {useSelector} from "react-redux"
import HttpService from "../../../../http/HttpService"
import {getProcessCounts, getProcessToDisplay} from "../../../../reducers/selectors/graph"
import {NodeId, SubprocessNodeType} from "../../../../types"
import ErrorBoundary from "../../../common/ErrorBoundary"
import {ProcessType} from "../../../Process/types"
import NodeUtils from "../../NodeUtils"
import {SubProcessGraph as BareGraph} from "../../SubProcessGraph"

export function SubprocessContent({
  nodeToDisplay,
  currentNodeId,
}: { nodeToDisplay: SubprocessNodeType, currentNodeId: NodeId }): JSX.Element {
  const {properties: {subprocessVersions}} = useSelector(getProcessToDisplay)
  const processCounts = useSelector(getProcessCounts)

  const [subprocessContent, setSubprocessContent] = useState<ProcessType>(null)

  useEffect(
    () => {
      if (NodeUtils.nodeIsSubprocess(nodeToDisplay)) {
        const subprocessVersion = subprocessVersions[nodeToDisplay.ref.id]
        HttpService.fetchProcessDetails(nodeToDisplay.ref.id, subprocessVersion).then(response => {
          setSubprocessContent(response.data.json)
        })
      }
    },
    [nodeToDisplay, subprocessVersions],
  )

  const subprocessCounts = (processCounts[currentNodeId] || {}).subprocessCounts || {}

  return (
    <ErrorBoundary>
      {subprocessContent && (
        <BareGraph
          processCounts={subprocessCounts}
          processToDisplay={subprocessContent}
        />
      )}
    </ErrorBoundary>
  )
}
