import {isEmpty, isObject, join} from "lodash"
import React from "react"
import InlinedSvgs from "../../../../assets/icons/InlinedSvgs"
import NodeTip from "../NodeTip"
import TestValue from "./TestValue"
import {useTestResults} from "../TestResultsWrapper"
import {NodeId} from "../../../../types"
import {NodeTableBody} from "../NodeDetailsContent/NodeTable"

export default function TestResults({nodeId}: { nodeId: NodeId }): JSX.Element {
  const results = useTestResults()

  if (!results.testResultsToShow || isEmpty(results.testResultsToShow.context.variables)) {
    return null
  }

  return (
    <NodeTableBody className="node-test-results">
      <div className="node-row">
        <div className="node-label">
          <NodeTip title={"Variables in test case"} icon={InlinedSvgs.tipsInfo}/>
        </div>
      </div>
      {
        Object.keys(results.testResultsToShow.context.variables).map((key, ikey) => (
          <div className="node-row" key={ikey}>
            <div className="node-label">{key}:</div>
            <TestValue value={results.testResultsToShow.context.variables[key]} shouldHideTestResults={false}/>
          </div>
        ))
      }
      {
        results.testResultsToShow && !isEmpty(results.testResultsToShow.mockedResultsForCurrentContext) ?
          results.testResultsToShow.mockedResultsForCurrentContext.map((mockedValue, index) => (
            <span key={index} className="testResultDownload">
              <a
                download={`${nodeId}-single-input`}
                href={downloadableHref(stringifyMockedValue(mockedValue))}
              >
                <span className="glyphicon glyphicon-download"/> Results for this input</a></span>
          )) :
          null
      }
      {
        results.testResultsToShow && !isEmpty(results.testResultsToShow.mockedResultsForEveryContext) ?
          (
            <span className="testResultDownload">
              <a
                download={`${nodeId}-all-inputs`}
                href={downloadableHref(mergedMockedResults(results.testResultsToShow.mockedResultsForEveryContext))}
              >
                <span className="glyphicon glyphicon-download"/> Results for all inputs</a></span>
          ) :
          null
      }
    </NodeTableBody>
  )

  function mergedMockedResults(mockedResults) {
    return join(mockedResults.map((mockedValue) => stringifyMockedValue(mockedValue)), "\n\n")
  }

  function downloadableHref(content) {
    return `data:application/octet-stream;charset=utf-8,${encodeURIComponent(content)}`
  }

  function stringifyMockedValue(mockedValue) {
    const content = mockedValue.value.pretty
    return isObject(content) ? JSON.stringify(content) : content
  }
}
