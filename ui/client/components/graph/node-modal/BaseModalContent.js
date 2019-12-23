import React from 'react'
import PropTypes from "prop-types"
import NodeErrors from "./NodeErrors"

export default function BaseModalContent(props) {

  const {edge, edgeErrors, readOnly, isMarked, changeEdgeTypeValue} = props

  return (
    <div className="node-table">
      <NodeErrors errors={edgeErrors} message={"Edge has errors"}/>
      <div className="node-table-body">
        <div className="node-row">
          <div className="node-label">From</div>
          <div className="node-value"><input readOnly={true} type="text" className="node-input" value={edge.from}/>
          </div>
        </div>
        <div className="node-row">
          <div className="node-label">To</div>
          <div className="node-value"><input readOnly={true} type="text" className="node-input" value={edge.to}/></div>
        </div>
        <div className="node-row">
          <div className="node-label">Type</div>
          <div className={"node-value" + (isMarked("edgeType.type") ? " marked" : "")}>
            <select
              id="processCategory"
              disabled={readOnly}
              className="node-input"
              value={edge.edgeType.type}
              onChange={(e) => changeEdgeTypeValue(e.target.value)}
            >
              <option value={"SwitchDefault"}>Default</option>
              <option value={"NextSwitch"}>Condition</option>
            </select>
          </div>
        </div>
        {props.children}
      </div>
    </div>
  )
}

BaseModalContent.propTypes = {
  edge: PropTypes.object,
  edgeErrors: PropTypes.array,
  readOnly: PropTypes.bool,
  isMarked: PropTypes.func,
  changeEdgeTypeValue: PropTypes.func,
}