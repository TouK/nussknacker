import React, {Component} from "react"
import {connect} from "react-redux"
import _ from "lodash"
import ActionsUtils from "../../actions/ActionsUtils"

class SideNodeDetails extends Component {

  flatObject = (obj) => {
    return _.flatMap(obj, (val, key) => {
      if (_.isArray(val)) {
        if (_.isEqual(key, "ids")) {
          return val.join(",")
        } else if (val.every(value => typeof value === "string")) {
          return _.concat(new FlatObjectEntry(null, key), val.map(nodeName => new FlatObjectEntry("id", nodeName)))
        } else {
          return _.concat(new FlatObjectEntry(null, key), _.flatMap(val, this.flatObject))
        }
      } else if (_.isObject(val)) {
        if (_.isEqual(key, "expression")) {
          return [new FlatObjectEntry(key, _.get(val, "expression"))]
        } else if (_.isEqual(key, "endResult")) {
          return [new FlatObjectEntry(null, key), new FlatObjectEntry("expression", _.get(val, "expression"))]
        } else {
          return _.concat(new FlatObjectEntry(null, key), this.flatObject(val))
        }
      } else {
        return [new FlatObjectEntry(key, val)]
      }
    })
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
                <p className="node-key">{`${obj.key  }: `}</p>
                <p className="node-value">{obj.value ? obj.value.toString() : obj.value}</p>
              </div>
            )
          }
        })}
        <hr/>
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
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SideNodeDetails)