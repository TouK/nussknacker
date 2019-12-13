import React from 'react'
import PropTypes from 'prop-types'
import _ from 'lodash'
import "ladda/dist/ladda.min.css"
import ModalRenderUtils from "./ModalRenderUtils"
import {notEmptyValidator} from "../../../common/Validators"
import EditableExpression from "./editors/expression/EditableExpression"

export default class EdgeDetailsContent extends React.Component {
  static propTypes = {
    edge: PropTypes.object.isRequired,
    readOnly: PropTypes.bool.isRequired,
    updateEdgeProp: PropTypes.func.isRequired,
    changeEdgeTypeValue: PropTypes.func.isRequired,
    pathsToMark: PropTypes.array,
    showValidation: PropTypes.bool.isRequired,
    showSwitch: PropTypes.bool.isRequired
  }

  isMarked = (path) => {
    return _.includes(this.props.pathsToMark, path)
  }

  baseModalContent(toAppend) {
    const { edge, edgeErrors, readOnly, changeEdgeTypeValue } = this.props

    return (
      <div className="node-table">
        {ModalRenderUtils.renderOtherErrors(edgeErrors, "Edge has errors")}
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
    const {edge, readOnly, updateEdgeProp, showValidation, showSwitch} = this.props

    switch (_.get(edge.edgeType, 'type')) {
      case "SwitchDefault": {
        return this.baseModalContent()
      }
      case "NextSwitch": {
        return this.baseModalContent(
          <EditableExpression
            fieldType={"expression"}
            fieldLabel={"Expression"}
            renderFieldLabel={this.renderFieldLabel}
            expressionObj={{expression: edge.edgeType.condition.expression, language: edge.edgeType.condition.language}}
            readOnly={readOnly}
            validators={[notEmptyValidator]}
            isMarked={this.isMarked("edgeType.condition.expression")}
            showValidation={showValidation}
            showSwitch={showSwitch}
            onValueChange={(newValue) => updateEdgeProp("edgeType.condition.expression", newValue)}
          />
        )
      }
      default:
        return ''
    }
  }

  renderFieldLabel = (label) => <div className="node-label">{label}</div>
}

const edgeName = (edge) => edge.from + "-" + edge.to