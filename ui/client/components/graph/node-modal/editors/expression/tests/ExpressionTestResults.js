import React from "react"
import ModalRenderUtils from "../../../ModalRenderUtils"
import TestValue from "./TestValue"

export default function ExpressionTestResults(props) {
  const {fieldName, testResultsToShow, testResultsToHide, toggleTestResult} = props
  const testValue = fieldName ? (testResultsToShow && testResultsToShow.expressionResults[fieldName]) : null
  const shouldHideTestResults = testResultsToHide.has(fieldName)
  const showIconClass = shouldHideTestResults ? "glyphicon glyphicon-eye-close" : "glyphicon glyphicon-eye-open"

  return (
    testValue ? (
      <div>
        {props.children}
        <div className="node-row node-test-results">
          <div className="node-label">{ModalRenderUtils.renderInfo('Value evaluated in test case')}
            {testValue.pretty ? <span className={showIconClass} onClick={e => toggleTestResult(fieldName)}/> : null}
          </div>
          <TestValue testValue={testValue} shouldHideTestResults={shouldHideTestResults}/>
        </div>
      </div>
    ) : props.children
  )
}