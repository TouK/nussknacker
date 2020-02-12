import React from "react"
import InlinedSvgs from "../../../../assets/icons/InlinedSvgs"
import NodeTip from "../NodeTip"

export default function TestErrors(props) {

  const {resultsToShow} = props

  return (resultsToShow && resultsToShow.error) ?
    <div className="node-table-body">
      <div className="node-row">
        <div className="node-label">
          <NodeTip title={"Test case error"} icon={InlinedSvgs.tipsWarning}/>
        </div>
        <div className="node-value">
          <div className="node-error">{resultsToShow.error}</div>
        </div>
      </div>
    </div> : null
}
