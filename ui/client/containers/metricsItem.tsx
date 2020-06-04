import TableRowIcon from "../components/table/TableRowIcon"
import {showMetrics} from "../actions/nk"
import {useDispatch} from "react-redux"
import React, {useCallback} from "react"

export function MetricsItem({process}: { process: $TodoType }) {
  const dispatch = useDispatch()
  const clickHandler = useCallback(
    () => dispatch(showMetrics(process.name)),
    [process],
  )
  return (
    <TableRowIcon glyph={"stats"} title="Show metrics" onClick={clickHandler}/>
  )
}
