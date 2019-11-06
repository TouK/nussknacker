import React from 'react'
import PropTypes from 'prop-types'
import _ from 'lodash'
import "ladda/dist/ladda.min.css"
import ExpressionSuggest from './ExpressionSuggest'
import ModalRenderUtils from "./ModalRenderUtils"
import {notEmptyValidator} from "../../common/Validators";

export default class EdgeDetailsContent extends React.Component {
  static propTypes = {
    edge: PropTypes.object.isRequired,
    readOnly: PropTypes.bool.isRequired,
    updateEdgeProp: PropTypes.func.isRequired,
    changeEdgeTypeValue: PropTypes.func.isRequired,
    pathsToMark: PropTypes.array
  }

  isMarked = (path) => {
    return _.includes(this.props.pathsToMark, path)
  }

  baseModalContent(toAppend) {
    const { edge, edgeErrors, readOnly, changeEdgeTypeValue } = this.props

    return (
      <div className="node-table">
        {ModalRenderUtils.renderErrors(edgeErrors, "Edge has errors")}
        <div className="node-table-body">
          <div className="node-row">
            <div className="node-label">From</div>
            <div className="node-value"><input readOnly={true} type="text" className="node-input" value={edge.from}/></div>
          </div>
          <div className="node-row">
            <div className="node-label">To</div>
            <div className="node-value"><input readOnly={true} type="text" className="node-input" value={edge.to}/></div>
          </div>
          <div className="node-row">
            <div className="node-label">Type</div>
            <div className={"node-value" + (this.isMarked("edgeType.type") ? " marked" : "")}>
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
          {toAppend}
        </div>
      </div>
    )
  }

  render() {
    const { edge, readOnly, updateEdgeProp } = this.props

    switch (_.get(edge.edgeType, 'type')) {
      case "SwitchDefault": {
        return this.baseModalContent()
      }
      case "NextSwitch": {
        return this.baseModalContent(
          <div className="node-row">
            <div className="node-label">Expression</div>
            <div className={"node-value" + (this.isMarked("edgeType.condition.expression") ? " marked" : "")}>
              <ExpressionSuggest
                inputProps={{
                  rows: 1,
                  cols: 50,
                  className: "node-input",
                  value: edge.edgeType.condition.expression,
                  onValueChange: (newValue) => updateEdgeProp("edgeType.condition.expression", newValue),
                  language: edge.edgeType.condition.language, readOnly: readOnly}}
                validators={[notEmptyValidator]}
              />
            </div>
          </div>
        )
      }
      default:
        return ''
    }
  }
}

const edgeName = (edge) => edge.from + "-" + edge.to