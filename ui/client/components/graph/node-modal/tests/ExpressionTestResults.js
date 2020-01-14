import React from "react"
import TestValue from "./TestValue"
import NodeTip from "../NodeTip"
import InlinedSvgs from "../../../../assets/icons/InlinedSvgs"

export default function ExpressionTestResults(props) {
  const {fieldName, resultsToShow, resultsToHide, toggleResult} = props
  const testValue = fieldName ? (resultsToShow && resultsToShow.expressionResults[fieldName]) : null
  const shouldHideTestResults = resultsToHide.has(fieldName)
  const showIconClass = shouldHideTestResults ? "glyphicon glyphicon-eye-close" : "glyphicon glyphicon-eye-open"

  return (
    testValue ? (
      <div>
        {props.children}
        <div className="node-row node-test-results">
          <div className="node-label">
            <NodeTip title={"Value evaluated in test case"} icon={InlinedSvgs.tipsInfo}/>
            {testValue.pretty ? <span className={showIconClass} onClick={e => toggleResult(fieldName)}/> : null}
          </div>
          <TestValue value={testValue} shouldHideTestResults={shouldHideTestResults}/>
        </div>
      </div>
    ) : props.children
  )
}