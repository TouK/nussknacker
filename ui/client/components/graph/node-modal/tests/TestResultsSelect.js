import React from "react"
import TestResultUtils from "../../../../common/TestResultUtils"

export default function TestResultsSelect(props) {

  const {testResults, testResultsIdToShow, selectTestResults} = props

  return TestResultUtils.hasTestResults(testResults) ?
    <div className="node-row">
      <div className="node-label">Test case:</div>
      <div className="node-value">
        <select className="node-input selectTestResults"
                onChange={(e) => selectTestResults(e.target.value, testResults)}
                value={testResultsIdToShow}>
          {
            TestResultUtils.availableContexts(testResults).map((ctx, idx) => (
              <option key={idx} value={ctx.id}>{ctx.id} ({ctx.display})</option>)
            )
          }
        </select>
      </div>
    </div> : null
}