import React, {PropTypes, Component} from "react";
import {render} from "react-dom";
import {connect} from "react-redux";
import _ from "lodash";
import {bindActionCreators} from "redux";
import ActionsUtils from "../../actions/ActionsUtils";
import NodeUtils from '../graph/NodeUtils'


class SideNodeDetails extends Component {

  flatObject = (obj) => {
    return _.flatMap(_.toPairs(obj), (keyVal) => {
      const key = keyVal[0]
      const val = keyVal[1]
      if (_.isArray(val)) {
        return _.concat(new FlatObjectEntry(null, key), _.flatMap(val, (v) => {return this.flatObject(v)}))
      } else if (_.isObject(val)) {
        if (_.isEqual(key, "expression")) {
          return [new FlatObjectEntry(key, _.get(val, "expression"))]
        } else if(_.isEqual(key, "endResult")) {
          return [new FlatObjectEntry(null, key), new FlatObjectEntry("expression", _.get(val, "expression"))]
        }
        else {
          return _.concat(new FlatObjectEntry(null, key), this.flatObject(val))
        }
      } else {
        return [new FlatObjectEntry(key, val)]
      }
    })
  }

  editNode = () => {
    this.props.actions.displayModalNodeDetails(this.props.nodeToDisplay)
  }

  deleteNode = () => {
    this.props.actions.deleteNode(this.props.nodeToDisplay.id)
  }

  deleteButton() {
    if (!NodeUtils.nodeIsProperties(this.props.nodeToDisplay)) {
      return (<button type="button" className="espButton" onClick={this.deleteNode}>Delete</button>)
    }
  }

  render() {
    const flatten = this.flatObject(this.props.nodeToDisplay)
    return (
      <div className="sideNodeDetails">
        {flatten.map((obj, index) => {
          if (obj.isSeparator) {
            return (
              <div key={index}>
                <hr/>
                <p className="node-label">{obj.value}</p>
              </div>
            )
          } else {
            return (
              <div key={index}>
                {index == 0 ? <p className="node-label">Main</p> : null}
                <p className="node-key">{obj.key + ": "}</p>
                <p className="node-value">{obj.value ? obj.value.toString() : obj.value}</p>
              </div>
            )
          }
        })}
        <hr/>
        <button type="button" className="espButton" onClick={this.editNode}>Edit</button>
        { this.deleteButton() }

      </div>
    )
  }
}

class FlatObjectEntry {
  constructor(key, value) {
    this.key = key
    this.value = value
    this.isSeparator = !this.key
  }
}
function mapState(state) {
  return {
    nodeToDisplay: state.graphReducer.nodeToDisplay
  };
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SideNodeDetails);