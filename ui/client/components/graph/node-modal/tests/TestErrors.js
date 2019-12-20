import ModalRenderUtils from "../ModalRenderUtils"
import React from "react"

export default function TestErrors(props) {

  const {testResultsToShow} = props

  return (testResultsToShow && testResultsToShow.error) ?
    <div className="node-table-body">
      <div className="node-row">
        <div className="node-label">{ModalRenderUtils.renderWarning('Test case error')} </div>
        <div className="node-value">
          <div className="node-error">{testResultsToShow.error}</div>
        </div>
      </div>
    </div> : null
}