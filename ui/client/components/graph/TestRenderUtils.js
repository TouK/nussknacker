import React from 'react'
import _ from "lodash"

import ModalRenderUtils from "./node-modal/ModalRenderUtils"
import TestResultUtils from "../../common/TestResultUtils"
import TestValue from "./node-modal/editors/expression/tests/TestValue"

export function testResults(nodeId, testResultsToShow) {
  if (testResultsToShow && !_.isEmpty(testResultsToShow.context.variables)) {
    const ctx = testResultsToShow.context.variables;
    return (

      <div className="node-table-body node-test-results">
        <div className="node-row">
          <div className="node-label">{ModalRenderUtils.renderInfo('Variables in test case')}</div>
        </div>
        {Object.keys(ctx).map((key, ikey) =>
          <div className="node-row" key={ikey}>
            <div className="node-label">{key}:</div>
            <TestValue testValue={ctx[key]} shouldHideTestResults={false}/>
          </div>
        )}
        {testResultsToShow && !_.isEmpty(testResultsToShow.mockedResultsForCurrentContext) ?
          (testResultsToShow.mockedResultsForCurrentContext).map((mockedValue, index) =>
            <span className="testResultDownload">
            <a download={nodeId + "-single-input"} key={index} href={downloadableHref(mockedValue.value.pretty)}>
              <span className="glyphicon glyphicon-download"/> Results for this input</a></span>
          ) : null
        }
        {testResultsToShow && !_.isEmpty(testResultsToShow.mockedResultsForEveryContext) ?
          <span className="testResultDownload">
            <a download={nodeId + "-all-inputs"}
               href={downloadableHref(mergedMockedResults(testResultsToShow.mockedResultsForEveryContext))}>
            <span className="glyphicon glyphicon-download"/> Results for all inputs</a></span>
          : null
        }
      </div>)
  } else {
    return null;
  }
}

function mergedMockedResults(mockedResults) {
  return _.join(mockedResults.map((mockedValue) => mockedValue.value.pretty), "\n\n")
}

function downloadableHref(content) {
  return "data:application/octet-stream;charset=utf-8," + encodeURIComponent(content)
}

export function testResultsSelect(testResults, testResultsIdToShow, selectTestResults) {
  if (hasTestResults(testResults)) {
    return (
      <div className="node-row">
        <div className="node-label">Test case:</div>
        <div className="node-value">
          <select className="node-input selectTestResults" onChange={(e) => selectTestResults(e.target.value, testResults)}
                  value={testResultsIdToShow}>
            { TestResultUtils.availableContexts(testResults).map((ctx, idx) =>
              (<option key={idx} value={ctx.id}>{ctx.id} ({ctx.display})</option>)
            )}
          </select>
        </div>
      </div>
    )
  } else {
    return null;
  }

}

export function stateForSelectTestResults(id, testResults) {
  if (hasTestResults(testResults)) {
    const chosenId = id || _.get(_.head(TestResultUtils.availableContexts(testResults)), "id")
    return {
      testResultsToShow: TestResultUtils.nodeResultsForContext(testResults, chosenId),
      testResultsIdToShow: chosenId
    }
  } else {
    return null;
  }
}

function hasTestResults(testResults) {
  return testResults && TestResultUtils.availableContexts(testResults).length > 0
}

export function testErrors(testResultsToShow) {
  if (testResultsToShow && testResultsToShow.error) {
    return (
      <div className="node-table-body">
        <div className="node-row">
          <div className="node-label">{ ModalRenderUtils.renderWarning('Test case error')} </div>
          <div className="node-value">
            <div className="node-error">{testResultsToShow.error}</div>
          </div>
        </div>
      </div>
    );
  } else {
    return null;
  }
}

