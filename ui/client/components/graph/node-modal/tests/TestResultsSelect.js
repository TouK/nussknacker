import React from "react"
import TestResultUtils from "../../../../common/TestResultUtils"
import {SelectWithFocus} from "../../../withFocus"

export default function TestResultsSelect(props) {

  const {results, resultsIdToShow, selectResults} = props

  return TestResultUtils.hasTestResults(results) ? (
    <div className="node-row">
      <div className="node-label">Test case:</div>
      <div className="node-value">
        <SelectWithFocus
          className="node-input selectResults"
          onChange={(e) => selectResults(e.target.value, results)}
          value={resultsIdToShow}
        >
          {
            TestResultUtils.availableContexts(results).map((ctx, idx) => (
              <option key={idx} value={ctx.id}>{ctx.id} ({ctx.display})</option>))
          }
        </SelectWithFocus>
      </div>
    </div>
  ) : null
}
