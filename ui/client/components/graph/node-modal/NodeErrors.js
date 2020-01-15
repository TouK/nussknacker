import _ from "lodash"
import React from "react"
import InlinedSvgs from "../../../assets/icons/InlinedSvgs"
import NodeTip from "./NodeTip"

export default class NodeErrors extends React.Component {
  render() {
    const {errors, errorMessage} = this.props

    return !_.isEmpty(errors) ?
      <div className="node-table-body">
        <div className="node-label">
          <NodeTip title={errorMessage} icon={InlinedSvgs.tipsError} className={"node-error-tip"}/>
        </div>
        <div className="node-value">
          <div>
            {
              errors.map((error, index) =>
                (<div className="node-error" key={index}
                      title={error.description}>{error.message + (error.fieldName ? ` (field: ${error.fieldName})` : "")}</div>))
            }
          </div>
        </div>
      </div> : null
  }
}
