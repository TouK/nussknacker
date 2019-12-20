import _ from "lodash"
import ModalRenderUtils from "../ModalRenderUtils"
import TestValue from "./TestValue"
import React from "react"

export default function TestResults(props) {
  const {nodeId, testResultsToShow} = props

  return (
    testResultsToShow && !_.isEmpty(testResultsToShow.context.variables) ?
      <div className="node-table-body node-test-results">
        <div className="node-row">
          <div className="node-label">{ModalRenderUtils.renderInfo('Variables in test case')}</div>
        </div>
        {
          Object.keys(testResultsToShow.context.variables).map((key, ikey) =>
            <div className="node-row" key={ikey}>
              <div className="node-label">{key}:</div>
              <TestValue testValue={testResultsToShow.context.variables[key]} shouldHideTestResults={false}/>
            </div>)
        }
        {
          testResultsToShow && !_.isEmpty(testResultsToShow.mockedResultsForCurrentContext) ?
            (testResultsToShow.mockedResultsForCurrentContext).map((mockedValue, index) =>
                <span className="testResultDownload">
            <a download={nodeId + "-single-input"} key={index} href={downloadableHref(mockedValue.value.pretty)}>
              <span className="glyphicon glyphicon-download"/> Results for this input</a></span>
            ) : null
        }
        {
          testResultsToShow && !_.isEmpty(testResultsToShow.mockedResultsForEveryContext) ?
            <span className="testResultDownload">
            <a download={nodeId + "-all-inputs"}
               href={downloadableHref(mergedMockedResults(testResultsToShow.mockedResultsForEveryContext))}>
            <span className="glyphicon glyphicon-download"/> Results for all inputs</a></span>
            : null
        }
      </div> : null
  )


  function mergedMockedResults(mockedResults) {
    return _.join(mockedResults.map((mockedValue) => mockedValue.value.pretty), "\n\n")
  }

  function downloadableHref(content) {
    return "data:application/octet-stream;charset=utf-8," + encodeURIComponent(content)
  }
}