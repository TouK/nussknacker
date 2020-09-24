import {flatMap, isArray, isObject, isString} from "lodash"
import React from "react"
import {connect} from "react-redux"
import ActionsUtils from "../../../actions/ActionsUtils"
import styles from "../../../stylesheets/panel-process-info.styl"
import {ListSeparator} from "./ListSeparator"

enum KnownKeys {
  expression = "expression",
  endResult = "endResult",
  ids = "ids",
  id = "id",
}

type Obj = {value: unknown, isSeparator?: boolean, key?: string}

function createObject(value: unknown, key?: string): Obj {
  return {key, value, isSeparator: !key}
}

function flattenObject(obj): Obj[] {
  return flatMap(obj, (value, key) => {
    if (isArray(value)) {
      if (key === KnownKeys.ids) {
        return createObject(value.join(","))
      }
      return value.every(isString) ?
        [createObject(key), ...value.map(name => createObject(name, KnownKeys.id))] :
        [createObject(key), ...flatMap(value, flattenObject)]
    }
    if (isObject(value)) {
      switch (key) {
        case KnownKeys.expression:
          return [createObject(value[KnownKeys.expression], key)]
        case KnownKeys.endResult:
          return [createObject(key), createObject(value[KnownKeys.expression], KnownKeys.expression)]
        default:
          return [createObject(key), ...flattenObject(value)]
      }
    }
    return [createObject(value, key)]
  })
}

function SideNodeDetails(props) {
  const flatten = flattenObject(props.nodeToDisplay)
  console.log(props.nodeToDisplay, flatten)
  return (
    <div className={styles.sideNodeDetails}>
      {flatten.map(({isSeparator, key, value}, index) => isSeparator ?
        (
          <div key={index}>
            <ListSeparator dark/>
            <p className={styles.nodeLabel}>{value}</p>
          </div>
        ) :
        (
          <div key={index}>
            {index == 0 && <p className={styles.nodeLabel}>Main</p>}
            <p className={styles.nodeKey}>{`${key}: `}</p>
            <p className={styles.nodeValue}>{value ? value.toString() : value}</p>
          </div>
        ))}
      <ListSeparator dark/>
    </div>
  )
}

function mapState(state) {
  return {
    nodeToDisplay: state.graphReducer.nodeToDisplay,
  }
}

export default connect(mapState, ActionsUtils.mapDispatchWithEspActions)(SideNodeDetails)
