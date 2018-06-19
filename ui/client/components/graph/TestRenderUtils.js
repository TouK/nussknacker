import React from 'react';
import Textarea from "react-textarea-autosize";
import _ from "lodash";

import ModalRenderUtils from "./ModalRenderUtils";
import TestResultUtils from "../../common/TestResultUtils";

export function wrapWithTestResult(fieldName, testResultsToShow, testResultsToHide, toggleTestResult, field) {
  const testValue = fieldName ? (testResultsToShow && testResultsToShow.expressionResults[fieldName]) : null
  const shouldHideTestResults = testResultsToHide.has(fieldName)
  const hiddenClassPart = (shouldHideTestResults ? " partly-hidden" : "")
  const showIconClass = shouldHideTestResults ? "glyphicon glyphicon-eye-close" : "glyphicon glyphicon-eye-open"
  if (testValue) {
    return (
      <div >
        {field}
        <div className="node-row node-test-results">
          <div className="node-label">{ModalRenderUtils.renderInfo('Value evaluated in test case')}
            {testValue.pretty ? <span className={showIconClass} onClick={e => toggleTestResult(fieldName)}/> : null}
          </div>
          <div className={"node-value" + hiddenClassPart}>
            {testValue.original ? <Textarea className="node-input" readOnly={true} value={testValue.original}/> : null}
            <Textarea rows={1} cols={50} className="node-input" value={testValue.pretty || testValue} readOnly={true}/>
            {shouldHideTestResults ? <div className="fadeout"></div> : null}
          </div>
        </div>
      </div>
    )
  } else {
    return field
  }
}

export function testResults(nodeId, testResultsToShow) {
  if (testResultsToShow && !_.isEmpty(testResultsToShow.context.variables)) {
    var ctx = testResultsToShow.context.variables
    return (

      <div className="node-table-body node-test-results">
        <div className="node-row">
          <div className="node-label">{ModalRenderUtils.renderInfo('Variables in test case')}</div>
        </div>
        {
          Object.keys(ctx).map((key, ikey) => {
            return (<div className="node-row" key={ikey}>
                <div className="node-label">{key}:</div>
                <div className="node-value">
                  {(ctx[key] || {}).original ? <Textarea className="node-input" readOnly={true} value={ctx[key].original}/> : null}
                  <Textarea className="node-input" readOnly={true} value={ctx[key] !== null ? (ctx[key].pretty || ctx[key]) : "null"}/>
                </div>
              </div>
            )
          })
        }
        {testResultsToShow && !_.isEmpty(testResultsToShow.mockedResultsForCurrentContext) ?
          (testResultsToShow.mockedResultsForCurrentContext).map((mockedValue, index) =>
            <span className="testResultDownload">
            <a download={nodeId + "-single-input"} key={index} href={downloadableHref(mockedValue.value)}>
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
  return _.join(mockedResults.map((mockedValue) => mockedValue.value), "\n\n")
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
              //toString is lame in some cases
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
    var error = testResultsToShow.error

    return (
      <div className="node-table-body">
        <div className="node-row">
          <div className="node-label">{ ModalRenderUtils.renderWarning('Test case error')} </div>
          <div className="node-value">
            <div className="node-error">{`${error.message} (${error.class})`}</div>
          </div>
        </div>
      </div>
    );
  } else {
    return null;
  }
}

