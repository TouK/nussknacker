import _ from "lodash"
import React from "react"
import InlinedSvgs from "../../../../assets/icons/InlinedSvgs"
import NodeTip from "../NodeTip"
import TestValue from "./TestValue"

export default function TestResults(props) {
  const {nodeId, resultsToShow} = props

  return (
    resultsToShow && !_.isEmpty(resultsToShow.context.variables) ?
      <div className="node-table-body node-test-results">
        <div className="node-row">
          <div className="node-label">
            <NodeTip title={"Variables in test case"} icon={InlinedSvgs.tipsInfo}/>
          </div>
        </div>
        {
          Object.keys(resultsToShow.context.variables).map((key, ikey) =>
            <div className="node-row" key={ikey}>
              <div className="node-label">{key}:</div>
              <TestValue value={resultsToShow.context.variables[key]} shouldHideTestResults={false}/>
            </div>)
        }
        {
          resultsToShow && !_.isEmpty(resultsToShow.mockedResultsForCurrentContext) ?
            (resultsToShow.mockedResultsForCurrentContext).map((mockedValue, index) =>
                <span className="testResultDownload">
            <a download={`${nodeId  }-single-input`} key={index} href={downloadableHref(mockedValue.value.pretty)}>
              <span className="glyphicon glyphicon-download"/> Results for this input</a></span>,
            ) : null
        }
        {
          resultsToShow && !_.isEmpty(resultsToShow.mockedResultsForEveryContext) ?
            <span className="testResultDownload">
            <a download={`${nodeId  }-all-inputs`}
               href={downloadableHref(mergedMockedResults(resultsToShow.mockedResultsForEveryContext))}>
            <span className="glyphicon glyphicon-download"/> Results for all inputs</a></span>
            : null
        }
      </div> : null
  )

  function mergedMockedResults(mockedResults) {
    return _.join(mockedResults.map((mockedValue) => mockedValue.value.pretty), "\n\n")
  }

  function downloadableHref(content) {
    return `data:application/octet-stream;charset=utf-8,${  encodeURIComponent(content)}`
  }
}
