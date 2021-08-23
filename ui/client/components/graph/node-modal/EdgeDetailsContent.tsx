import "ladda/dist/ladda.min.css"
import _ from "lodash"
import PropTypes from "prop-types"
import React from "react"
import BaseModalContent from "./BaseModalContent"
import EditableEditor from "./editors/EditableEditor"

export default class EdgeDetailsContent extends React.Component {
  static propTypes = {
    edge: PropTypes.object.isRequired,
    readOnly: PropTypes.bool.isRequired,
    updateEdgeProp: PropTypes.func.isRequired,
    changeEdgeTypeValue: PropTypes.func.isRequired,
    pathsToMark: PropTypes.array,
    showValidation: PropTypes.bool.isRequired,
    showSwitch: PropTypes.bool.isRequired,
    variableTypes: PropTypes.object.isRequired,
  }

  isMarked = (path) => {
    return _.includes(this.props.pathsToMark, path)
  }

  render() {
    const {edge, edgeErrors, readOnly, updateEdgeProp, showValidation, showSwitch, changeEdgeTypeValue, variableTypes} = this.props

    switch (_.get(edge.edgeType, "type")) {
      case "SwitchDefault": {
        return (
          <BaseModalContent
            edge={edge}
            edgeErrors={edgeErrors}
            readOnly={readOnly}
            isMarked={this.isMarked}
            changeEdgeTypeValue={changeEdgeTypeValue}
          />
        )
      }
      case "NextSwitch": {
        const expressionObj = {
          expression: edge.edgeType.condition.expression,
          language: edge.edgeType.condition.language,
        }
        return (
          <BaseModalContent
            edge={edge}
            edgeErrors={edgeErrors}
            readOnly={readOnly}
            isMarked={this.isMarked}
            changeEdgeTypeValue={changeEdgeTypeValue}
          >
            <EditableEditor
              variableTypes={variableTypes}
              fieldLabel={"Expression"}
              renderFieldLabel={this.renderFieldLabel}
              expressionObj={expressionObj}
              readOnly={readOnly}
              isMarked={this.isMarked("edgeType.condition.expression")}
              showValidation={showValidation}
              showSwitch={showSwitch}
              onValueChange={(newValue) => updateEdgeProp("edgeType.condition.expression", newValue)}
            />
          </BaseModalContent>
        )
      }
      default:
        return ""
    }
  }

  renderFieldLabel = (label) => <div className="node-label">{label}</div>
}

const edgeName = (edge) => `${edge.from  }-${  edge.to}`
