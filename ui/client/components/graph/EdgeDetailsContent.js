import React from 'react'
import PropTypes from 'prop-types'
import _ from 'lodash'
import "ladda/dist/ladda.min.css"
import ExpressionSuggest from './ExpressionSuggest'
import ModalRenderUtils from "./ModalRenderUtils"

export default class EdgeDetailsContent extends React.Component {
  static propTypes = {
    edge: PropTypes.object.isRequired,
    readOnly: PropTypes.bool.isRequired,
    updateEdgeProp: PropTypes.func.isRequired,
    changeEdgeTypeValue: PropTypes.func.isRequired,
    pathsToMark: PropTypes.array
  }

  constructor(props) {
    super(props)
  }

  isMarked = (path) => {
    return _.includes(this.props.pathsToMark, path)
  }

  render() {
    const { edge } = this.props
    const baseModalContent = (toAppend) => {
      return (
        <div className="node-table">
          {ModalRenderUtils.renderErrors(this.props.edgeErrors, "Edge has errors")}
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
                <select id="processCategory"
                        disabled={this.props.readOnly}
                        className="node-input"
                        value={edge.edgeType.type}
                        onChange={(e) => this.props.changeEdgeTypeValue(e.target.value)}>
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

    switch (_.get(edge.edgeType, 'type')) {
      case "SwitchDefault": {
        return baseModalContent()
      }
      case "NextSwitch": {
        return baseModalContent(
          <div className="node-row">
            <div className="node-label">Expression</div>
            <div className={"node-value" + (this.isMarked("edgeType.condition.expression") ? " marked" : "")}>
              <ExpressionSuggest inputProps={{
                rows: 1, cols: 50, className: "node-input", value: edge.edgeType.condition.expression,
                onValueChange: (newValue) => this.props.updateEdgeProp("edgeType.condition.expression", newValue),
                language: edge.edgeType.condition.language, readOnly: this.props.readOnly
              }}/>
            </div>
          </div>
        )
      }
      default:
        return ''
    }
  }
}