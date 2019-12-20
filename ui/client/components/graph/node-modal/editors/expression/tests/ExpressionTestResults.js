import React from "react"
import TestValue from "./TestValue"
import InlinedSvgs from "../../../../../../assets/icons/InlinedSvgs"
import NodeTip from "../../../NodeTip"

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
          <div className="node-label">
            <NodeTip title={'Value evaluated in test case'} icon={InlinedSvgs.tipsInfo}/>
            {testValue.pretty ? <span className={showIconClass} onClick={e => toggleTestResult(fieldName)}/> : null}
          </div>
          <TestValue testValue={testValue} shouldHideTestResults={shouldHideTestResults}/>
        </div>
      </div>
    ) : props.children
  )
}