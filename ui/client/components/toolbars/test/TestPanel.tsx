import React from "react"
import {useSelector} from "react-redux"
import {isSubprocess} from "../../../reducers/selectors/graph"
import {DefaultToolbarPanel} from "../DefaultToolbarPanel"

const TestPanel: typeof DefaultToolbarPanel = props => (
  <DefaultToolbarPanel {...props} isHidden={useSelector(isSubprocess)}/>
)

export default TestPanel
